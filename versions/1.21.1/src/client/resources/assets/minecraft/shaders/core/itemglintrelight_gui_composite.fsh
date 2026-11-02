#version 150

// 1.21.1 port of core/held_item_gui_composite.fsh.
//
// Differences from the 1.21.11 shader, all forced by the 1.21.1 shader backend:
//  * no std140 uniform block -- 1.21.1 takes plain uniforms from the shader JSON, so OutlineInfo is
//    split into the same vec4 layout (primaryColor..scrollBounds) plus eight named palette uniforms;
//  * 1.21.1 is normal-Z only, so the reverse-Z mirror (normalDepth) is gone;
//  * scene depth and the arm occluder are fixed: the GUI path never enables scene occlusion and has
//    no arm, so GUI_SCENE_DEPTH is the constant far plane the reference used.

uniform sampler2D MaskSampler;
uniform sampler2D BloomSampler;
uniform sampler2D ItemDepthSampler;

uniform vec4 primaryColor;
uniform vec4 secondaryColor;
uniform vec4 geometry;      // width, height, outline radius in mask pixels, alpha threshold
uniform vec4 animation;     // colour mode, time, scroll speed, corner softness
uniform vec4 effect;        // sample count, glow intensity, palette size, bloom intensity
uniform vec4 colorScroll;   // scroll direction x, y, interval, render mode (cubic)
uniform vec4 scrollMode;    // scroll mode, centre x, centre y, path radius
uniform vec4 scrollBounds;  // half width, half height, occlusion mode (always 1.0), colour scroll scale
uniform vec4 bloomParameters; // softness, bloom enabled, unused, unused
uniform vec4 palette0;
uniform vec4 palette1;
uniform vec4 palette2;
uniform vec4 palette3;
uniform vec4 palette4;
uniform vec4 palette5;
uniform vec4 palette6;
uniform vec4 palette7;

in vec2 texCoord;
out vec4 fragColor;

const int MAX_OUTLINE_SAMPLES = 48;
const float TAU = 6.28318530718;
const float GUI_SCENE_DEPTH = 1.0;

float coverage(vec2 uv) {
    return texture(MaskSampler, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

vec3 paletteColor(int index) {
    int slot = clamp(index, 0, 7);
    if (slot == 0) return palette0.rgb;
    if (slot == 1) return palette1.rgb;
    if (slot == 2) return palette2.rgb;
    if (slot == 3) return palette3.rgb;
    if (slot == 4) return palette4.rgb;
    if (slot == 5) return palette5.rgb;
    if (slot == 6) return palette6.rgb;
    return palette7.rgb;
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

float bloomAlpha(float coverageValue) {
    float softness = clamp(bloomParameters.x, 0.0, 1.0);
    float hardCoverage = coverageValue > 0.001 ? 1.0 : 0.0;
    if (softness <= 0.001) return hardCoverage;
    float normalizedCoverage = clamp(coverageValue / 0.30, 0.0, 1.0);
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
            nearestDepth = min(nearestDepth, texture(ItemDepthSampler, sampleUv).r);
        }
    }
    return smoothstep(nearestDepth - 0.0004, nearestDepth + 0.0003, GUI_SCENE_DEPTH);
}

// -------------------------------------------------------------------------------------------
// Cubic face lighting (shared contract with itemglintrelight_held_outline.fsh)
// -------------------------------------------------------------------------------------------
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
        int sampleCount = cubic ? cubicSampleCount() : outlineSampleCount();
        int ringCount = cubic || softness >= 0.999 ? 2 : 1;
        vec2 faceSum = vec2(0.0);
        for (int i = 0; i < MAX_OUTLINE_SAMPLES; i++) {
            if (i >= sampleCount) break;
            float angle = TAU * float(i) / float(sampleCount);
            vec2 circleDirection = vec2(cos(angle), sin(angle));
            vec2 squareDirection = circleDirection / max(max(abs(circleDirection.x), abs(circleDirection.y)), 0.0001);
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
