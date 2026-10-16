#version 330
#extension GL_ARB_separate_shader_objects : require


// GUI-only composite.
//
// GuiItemOutlineManager.compositePreviewToTexture() pins scrollBounds.z to the constant 1.0 on this
// path (writePreviewUniforms writes the literal), so sceneOcclusionEnabled() and
// thirdPersonOcclusion() are both false here. That makes the scene-depth, arm-occluder and
// item-depth visibility machinery of the shared outline and bloom shaders provably inert:
//
//   * held_item_outline.fsh computes nearestDepth inside every sample loop and then never reads it,
//     because the only reader sits inside the sceneOcclusionEnabled() block. Those ItemDepthSampler
//     fetches are roughly half of that pass' texture traffic.
//   * held_item_bloom_composite.fsh reads SceneDepthSampler (a 1x1 attachment cleared to the far
//     plane, so every read is exactly 1.0), tests it in the 3x3 loop and again at the end, and calls
//     armOcclusion() against a 1x1 all-zero stand-in, which returns 1.0 through its
//     maxCoverage <= 0.001 early-out.
//
// This shader drops all of that and, in addition, folds the bloom composite and the outline into a
// single pass. The two passes it replaces both wrote with TRANSLUCENT blending into the same cleared
// RGBA8 target, so their combined result is reproduced here analytically with each pass' 8-bit store
// applied explicitly.

uniform sampler2D MaskSampler;
uniform sampler2D BloomSampler;
uniform sampler2D ItemDepthSampler;

layout(std140) uniform OutlineInfo {
    vec4 primaryColor;
    vec4 secondaryColor;
    vec4 geometry;
    vec4 animation;
    vec4 effect;
    vec4 colorScroll;
    vec4 scrollMode;
    vec4 scrollBounds;
    vec4 materialPalette[8];
    vec4 bloomParameters;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// 26.2 renders with reverse-Z: glClipControl(GL_LOWER_LEFT, GL_ZERO_TO_ONE) plus a reversed
// projection puts the near plane at 1.0 and the far plane at 0.0, and every vanilla pipeline
// compares with GREATER_THAN_OR_EQUAL. The depth comparisons below are written for the 1.21.11
// convention (near 0.0, far 1.0), so sampled depth is mirrored back at the sampling boundary.
// The mapping is exact -- a reversed projection stores exactly 1 - z -- and normalDepth is the
// identity unless OutlineInfo.bloomParameters.z reports reverse-Z, which only 26.2 sets.
float normalDepth(float depth) {
    return bloomParameters.z > 0.5 ? 1.0 - depth : depth;
}

const int MAX_OUTLINE_SAMPLES = 48;
const float TAU = 6.28318530718;
// GuiItemOutlineManager.depthPlaceholderView() is a 1x1 depth attachment cleared to the far plane,
// so every SceneDepthSampler read on this path returns exactly this value.
const float GUI_SCENE_DEPTH = 1.0;

float coverage(vec2 uv) {
    return texture(MaskSampler, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

vec3 paletteColor(int index) {
    return materialPalette[clamp(index, 0, 7)].rgb;
}

float scrollPosition() {
    float normalizedInterval = clamp((colorScroll.z - 0.25) / 1.25, 0.0, 1.0);
    float segmentPixels = mix(72.0, 384.0, normalizedInterval);
    float timeOffset = animation.y * animation.z * 0.12;
    if (scrollMode.x < 0.5) {
        return dot(texCoord * geometry.xy * max(scrollBounds.w, 1.0), colorScroll.xy) / segmentPixels - timeOffset;
    }
    vec2 relative = (texCoord * geometry.xy - scrollMode.yz) / max(scrollBounds.xy, vec2(1.0));
    float angle = atan(relative.y, relative.x) - atan(colorScroll.y, colorScroll.x);
    float perimeter = TAU * max(scrollMode.w, 1.0) * max(scrollBounds.w, 1.0);
    float cycles = max(1.0, round(perimeter / (segmentPixels * 2.0)));
    return (angle / TAU + 0.5) * cycles * 2.0 - timeOffset;
}

vec3 hsvToRgb(float hue, float saturation, float value) {
    vec3 sector = abs(fract(vec3(hue) + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return value * mix(vec3(1.0), clamp(sector - 1.0, 0.0, 1.0), saturation);
}

vec3 paletteBlend(vec3 first, vec3 second, float blend) {
    vec3 linear = mix(pow(max(first, vec3(0.0)), vec3(2.2)), pow(max(second, vec3(0.0)), vec3(2.2)), blend);
    return pow(max(linear, vec3(0.0)), vec3(1.0 / 2.2));
}

float smoothRamp(float value) {
    float clamped = clamp(value, 0.0, 1.0);
    return clamped * clamped * clamped * (clamped * (clamped * 6.0 - 15.0) + 10.0);
}

vec3 twoColorRamp(float position) {
    float segment = floor(position);
    float local = fract(position);
    return mod(segment, 2.0) < 1.0
        ? paletteBlend(primaryColor.rgb, secondaryColor.rgb, smoothRamp(local))
        : paletteBlend(secondaryColor.rgb, primaryColor.rgb, smoothRamp(local));
}

vec3 materialRamp(float position) {
    float paletteSize = max(effect.z, 1.0);
    if (paletteSize < 1.5) return paletteColor(0);
    float segment = floor(position);
    int first = int(mod(segment, paletteSize));
    int second = int(mod(segment + 1.0, paletteSize));
    return paletteBlend(paletteColor(first), paletteColor(second), smoothRamp(fract(position)));
}

vec3 resolveColor() {
    float mode = animation.x;
    if (mode < 0.5) return primaryColor.rgb;
    float position = scrollPosition();
    if (mode < 1.5) {
        return twoColorRamp(position);
    }
    if (mode > 2.5) {
        return materialRamp(position);
    }
    return hsvToRgb(fract(position / 6.0), 0.88, 1.0);
}

float cornerSoftness() {
    return clamp(animation.w, 0.0, 1.0);
}

int outlineSampleCount() {
    return clamp(int(effect.x + 0.5), 8, MAX_OUTLINE_SAMPLES);
}

float glowIntensity() {
    return clamp(effect.y, 0.0, 2.0);
}

bool cubicMode() {
    return colorScroll.w > 0.5;
}

float bloomAlpha(float coverage) {
    float softness = clamp(bloomParameters.x, 0.0, 1.0);
    float hardCoverage = coverage > 0.001 ? 1.0 : 0.0;
    if (softness <= 0.001) return hardCoverage;
    float normalizedCoverage = clamp(coverage / 0.30, 0.0, 1.0);
    float alphaExponent = 0.25 + 1.35 * softness;
    return pow(normalizedCoverage, alphaExponent);
}

// In held_item_bloom_composite.fsh this is the three-line visibility block: the scene-depth test in
// the 3x3 loop always passes against the far-plane placeholder, the trailing discard never fires,
// armOcclusion() returns 1.0, and what is left is the depth gate below.
float bloomDepthGate(vec2 uv, vec2 texel) {
    float nearestDepth = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 sampleUv = clamp(uv + vec2(x, y) * texel, vec2(0.0), vec2(1.0));
            nearestDepth = min(nearestDepth, normalDepth(texture(ItemDepthSampler, sampleUv).r));
        }
    }
    return smoothstep(nearestDepth - 0.0004, nearestDepth + 0.0003, GUI_SCENE_DEPTH);
}

// -------------------------------------------------------------------------------------------
// Cubic face lighting (shared contract with held_item_outline.fsh)
// -------------------------------------------------------------------------------------------
// The cubic stroke is shaded as if the item sat inside an extruded box: the stroke's outward normal
// selects one of four faces, each with its own brightness. faceSum accumulates the ring directions
// that landed on covered pixels, so it points from the silhouette back towards the stroke pixel --
// along the outward normal. The previous version normalised a sum gathered over a solid box, and a
// symmetric covered neighbourhood cancels that sum to zero, so normalizing produced NaN; here a
// vanishing sum simply fades the shading back to flat and the face seams are anti-aliased on both
// axes instead of only along the diagonal.
const float CUBIC_LIGHT_NEG_X = 0.96;
const float CUBIC_LIGHT_POS_X = 0.72;
const float CUBIC_LIGHT_NEG_Y = 1.14;
const float CUBIC_LIGHT_POS_Y = 0.64;

float cubicFaceLight(vec2 faceSum) {
    float magnitude = length(faceSum);
    if (magnitude <= 0.0001) {
        return 1.0;
    }
    vec2 face = faceSum / magnitude;
    float axisDelta = abs(face.x) - abs(face.y);
    float axisSeam = max(fwidth(axisDelta) * 1.5, 0.02);
    float sideSeam = max(fwidth(face.x) * 1.5, 0.02);
    float verticalSeam = max(fwidth(face.y) * 1.5, 0.02);
    float horizontalLight = mix(CUBIC_LIGHT_NEG_X, CUBIC_LIGHT_POS_X, smoothstep(-sideSeam, sideSeam, face.x));
    float verticalLight = mix(CUBIC_LIGHT_NEG_Y, CUBIC_LIGHT_POS_Y, smoothstep(-verticalSeam, verticalSeam, face.y));
    float faceLight = mix(verticalLight, horizontalLight, smoothstep(-axisSeam, axisSeam, axisDelta));
    return mix(1.0, faceLight, smoothstep(0.0, 0.5, magnitude));
}

// Direction count for the cubic ring; see held_item_outline.fsh for the full reasoning. The GUI mask
// is small, so this normally resolves to the six-sample floor rather than a full quality ring.
int cubicSampleCount() {
    return clamp(int(ceil(geometry.z * 4.0)), 6, MAX_OUTLINE_SAMPLES);
}

void main() {
    vec2 texel = 1.0 / max(geometry.xy, vec2(1.0));
    float softness = cornerSoftness();
    ivec2 maskSize = textureSize(MaskSampler, 0);
    ivec2 centerPixel = clamp(ivec2(texCoord * vec2(maskSize)), ivec2(0), maskSize - ivec2(1));
    float center = softness <= 0.001
        ? texelFetch(MaskSampler, centerPixel, 0).a
        : smoothstep(geometry.w - 0.02, geometry.w + 0.02, coverage(texCoord));
    float outer = 0.0;
    float cubicLight = 1.0;
    bool cubic = cubicMode();
    if (!cubic && softness <= 0.001) {
        // Sharply-edged flat stroke: the square search, left exactly as it was.
        int sharpRadius = max(1, int(floor(geometry.z + 0.5)));
        for (int y = -sharpRadius; y <= sharpRadius; y++) {
            for (int x = -sharpRadius; x <= sharpRadius; x++) {
                ivec2 samplePixel = clamp(centerPixel + ivec2(x, y), ivec2(0), maskSize - ivec2(1));
                outer = max(outer, coverage((vec2(samplePixel) + 0.5) / vec2(maskSize)));
            }
        }
    } else {
        // Ring sampling, shared by the soft flat path and the cubic path. Cubic keeps the square
        // reach it had as a box but pays a cost set by the outline radius rather than by its square.
        // Its half-radius ring matters here too: rings at r and r/2 fill every distance in [0, r] for
        // anything at least r/2 thick, which the GUI mask always is. See held_item_outline.fsh.
        int sampleCount = cubic ? cubicSampleCount() : outlineSampleCount();
        int ringCount = cubic || softness >= 0.999 ? 2 : 1;
        vec2 faceSum = vec2(0.0);
        for (int i = 0; i < MAX_OUTLINE_SAMPLES; i++) {
            if (i >= sampleCount) break;
            float angle = TAU * float(i) / float(sampleCount);
            vec2 circleDirection = vec2(cos(angle), sin(angle));
            vec2 squareDirection = circleDirection / max(max(abs(circleDirection.x), abs(circleDirection.y)), 0.0001);
            // Cubic stays square at every softness, matching the box it replaces.
            vec2 sampleDirection = cubic ? squareDirection : mix(squareDirection, circleDirection, softness);
            for (int ring = 0; ring < 2; ring++) {
                if (ring >= ringCount) break;
                if (ring == 1 && (i & 1) != 0) break;
                float radius = geometry.z * (ring == 0 ? 1.0 : 0.5);
                vec2 sampleUv = texCoord + sampleDirection * texel * radius;
                float sampleCoverage = coverage(sampleUv);
                outer = max(outer, sampleCoverage);
                if (cubic && sampleCoverage > geometry.w) {
                    faceSum -= sampleDirection * (ring == 0 ? 1.0 : 0.5);
                }
            }
        }
        if (cubic) {
            cubicLight = cubicFaceLight(faceSum);
        }
    }
    // visible == 1: scene occlusion is disabled on this path.
    float outlineAlpha = smoothstep(geometry.w, 1.0, outer) * (1.0 - center) * primaryColor.a;

    float bloom = 0.0;
    if (bloomParameters.y > 0.5) {
        float blurredCoverage = max(texture(BloomSampler, texCoord).a - texture(MaskSampler, texCoord).a, 0.0);
        bloom = bloomAlpha(blurredCoverage) * effect.w * bloomDepthGate(texCoord, texel);
    }

    if (outlineAlpha <= 0.001 && bloom <= 0.001) discard;

    vec3 outlineColor = resolveColor();
    float chromaSafeGain = 1.0 / max(max(outlineColor.r, outlineColor.g), max(outlineColor.b, 0.0001));
    float brightnessGain = min(1.0 + glowIntensity() * 0.35, chromaSafeGain);
    vec3 stroke = outlineColor * brightnessGain * cubicLight;

    // Replay of the previous two blended passes:
    //   pass 1 (bloom, TRANSLUCENT over a cleared target):
    //       rgb = round8(color * min(bloom, 1)), a = round8(min(bloom, 1))
    //   pass 2 (outline, TRANSLUCENT over the result of pass 1):
    //       rgb = stroke * outlineAlpha + pass1.rgb * (1 - outlineAlpha)
    //       a   = outlineAlpha      + pass1.a   * (1 - outlineAlpha)
    // Both passes resolve the same colour for the same texCoord, which is what makes the fold exact.
    float bloomStored = min(bloom, 1.0);
    vec3 bloomRgb = floor(outlineColor * bloomStored * 255.0 + 0.5) / 255.0;
    float bloomAlphaStored = floor(bloomStored * 255.0 + 0.5) / 255.0;
    fragColor = vec4(stroke * outlineAlpha + bloomRgb * (1.0 - outlineAlpha),
                     outlineAlpha + bloomAlphaStored * (1.0 - outlineAlpha));
}
