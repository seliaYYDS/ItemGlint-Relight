#version 330

uniform sampler2D MaskSampler;
uniform sampler2D ItemDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D ArmOccluderSampler;
uniform sampler2D ArmOccluderDepthSampler;

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

in vec2 texCoord;
out vec4 fragColor;

const int MAX_OUTLINE_SAMPLES = 48;
const float TAU = 6.28318530718;

float coverage(vec2 uv) {
    return texture(MaskSampler, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

bool sceneOcclusionEnabled() {
    return scrollBounds.z < 0.5 || scrollBounds.z > 1.5;
}

bool thirdPersonOcclusion() {
    return scrollBounds.z > 1.5;
}

// Neighbor samples are potential outline sources. Do not let an item sample
// hidden by the scene contribute to the outer shell at an occluder boundary.
float visibleCoverage(vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(1.0));
    float sampleCoverage = texture(MaskSampler, clamped).a;
    if (sampleCoverage <= 0.0 || !sceneOcclusionEnabled()) {
        return sampleCoverage;
    }
    float itemDepth = texture(ItemDepthSampler, clamped).r;
    float sceneDepth = texture(SceneDepthSampler, clamped).r;
    return sceneDepth + 0.00015 < itemDepth ? 0.0 : sampleCoverage;
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

float armDepthAt(vec2 uv) {
    return texture(ArmOccluderDepthSampler, clamp(uv, vec2(0.0), vec2(1.0))).r;
}

float armDepthPresence(float depth) {
    return 1.0 - smoothstep(0.9998, 0.99998, depth);
}

// The arm pass writes coverage and depth, but a render type may write only one of them, so either
// one counts as "the arm is here". Same contract as the reference hand mask.
float armMaskAt(vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(1.0));
    vec4 armSample = texture(ArmOccluderSampler, clamped);
    float colorPresence = step(1.0 / 255.0, max(max(armSample.r, armSample.g), armSample.b));
    return max(max(armSample.a, colorPresence), armDepthPresence(armDepthAt(clamped)));
}

float armOccluderRadius() {
    return clamp(1.05 + geometry.z * 0.46 + cornerSoftness() * 0.32, 0.95, 4.8);
}

// Arm occlusion is decided by coverage first and only refined by depth.
// An outline pixel always sits next to the silhouette, so the item depth available there is the
// nearest item surface within the stroke's reach, while a plain test would compare it against a
// single arm texel. Those two are not comparable, and the result is a stroke left half transparent
// on top of the arm. Reading the arm conservatively instead -- nearest arm surface within one
// outline radius, its coverage used as a mask -- is what makes the arm hide the stroke outright.
float armOcclusion(float itemDepth, vec2 uv, vec2 texel) {
    vec2 reach = texel * armOccluderRadius();
    float centerCoverage = armMaskAt(uv);
    float maxCoverage = centerCoverage;
    float totalCoverage = centerCoverage;
    float armDepth = armDepthAt(uv);
    float tap;

    tap = armMaskAt(uv + vec2(reach.x, 0.0));
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv + vec2(reach.x, 0.0)));
    tap = armMaskAt(uv - vec2(reach.x, 0.0));
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv - vec2(reach.x, 0.0)));
    tap = armMaskAt(uv + vec2(0.0, reach.y));
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv + vec2(0.0, reach.y)));
    tap = armMaskAt(uv - vec2(0.0, reach.y));
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv - vec2(0.0, reach.y)));
    tap = armMaskAt(uv + reach);
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv + reach));
    tap = armMaskAt(uv - reach);
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv - reach));
    tap = armMaskAt(uv + vec2(reach.x, -reach.y));
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv + vec2(reach.x, -reach.y)));
    tap = armMaskAt(uv + vec2(-reach.x, reach.y));
    maxCoverage = max(maxCoverage, tap);
    totalCoverage += tap;
    armDepth = min(armDepth, armDepthAt(uv + vec2(-reach.x, reach.y)));

    if (maxCoverage <= 0.001) {
        return 1.0;
    }

    float averageCoverage = totalCoverage / 9.0;
    float featherCoverage = mix(maxCoverage, averageCoverage, 0.35);
    float armCoverage = smoothstep(0.035, 0.52, max(centerCoverage, featherCoverage));

    float coreOcclusion = smoothstep(0.10, 0.46, centerCoverage);
    float fringeOcclusion = smoothstep(0.22, 0.88, armCoverage);
    float maskVisibility = 1.0 - max(coreOcclusion, fringeOcclusion * 0.62);

    float validDepth = armDepthPresence(armDepth);
    if (validDepth <= 0.001) {
        return maskVisibility;
    }

    // The arm has to be genuinely nearer than the item before the depth term lifts the mask, and
    // even then it stays a minority vote: coverage owns this decision.
    float depthVisibility = smoothstep(itemDepth - 0.0035, itemDepth + 0.0008, armDepth);
    float depthContribution = mix(1.0, depthVisibility, (0.18 + 0.08 * smoothstep(0.18, 0.90, armCoverage)) * validDepth);
    return min(maskVisibility, depthContribution);
}

// -------------------------------------------------------------------------------------------
// Cubic face lighting
// -------------------------------------------------------------------------------------------
// The cubic stroke is shaded as if the item sat inside an extruded box: the stroke's outward normal
// selects one of four faces, each with its own brightness. faceSum accumulates the ring directions
// that landed on covered pixels, so it points from the silhouette back towards the stroke pixel --
// along the outward normal.
//
// The previous version normalised a sum gathered over a solid box. A symmetric covered
// neighbourhood cancels that sum to zero, and normalizing zero yields NaN, which is a large part of
// why the wrapping read as irregular. Here a vanishing sum simply fades the shading back to flat,
// the seams between the four faces are anti-aliased on both axes instead of only along the
// diagonal, and the reach follows the radius continuously instead of snapping to whole texels.
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
    // A direction that almost cancels out sits in the middle of a thick covered band, where the
    // stroke is at its most transparent; fading to flat keeps that transition continuous.
    return mix(1.0, faceLight, smoothstep(0.0, 0.5, magnitude));
}

// Direction count for the cubic ring. The stroke is traced with a square ring of perimeter 8r, so
// the count follows the outline radius rather than a fixed quality step: a thin GUI stroke stays at
// a handful of samples while a wide screen-space stroke gets a dense ring. The cubic path used to
// scan a solid box of radius round(geometry.z), which is quadratic in both the outline width and the
// screen resolution -- 169 samples per pixel at 1080p, 289 at 1440p and 625 at 4K for the bundled
// width-4.85 preset, against a fixed 12/24/48 for flat.
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
    float nearestDepth = 1.0;
    float cubicLight = 1.0;
    float glow = glowIntensity();
    bool cubic = cubicMode();
    if (!cubic && softness <= 0.001) {
        // Sharply-edged flat stroke: the square search, left exactly as it was.
        int sharpRadius = max(1, int(floor(geometry.z + 0.5)));
        for (int y = -sharpRadius; y <= sharpRadius; y++) {
            for (int x = -sharpRadius; x <= sharpRadius; x++) {
                ivec2 samplePixel = clamp(centerPixel + ivec2(x, y), ivec2(0), maskSize - ivec2(1));
                vec2 sampleUv = (vec2(samplePixel) + 0.5) / vec2(maskSize);
                float sampleCoverage = visibleCoverage(sampleUv);
                outer = max(outer, sampleCoverage);
                if (sampleCoverage > 0.0) {
                    nearestDepth = min(nearestDepth, texture(ItemDepthSampler, sampleUv).r);
                }
            }
        }
    } else {
        // Ring sampling, shared by the soft flat path and the cubic path.
        //
        // Cubic keeps the square reach it had as a box -- a square ring's corners sit at
        // geometry.z * sqrt(2), which is exactly where the box reached -- but pays a cost set by the
        // outline radius rather than by its square.
        //
        // The half-radius ring on odd directions is what keeps that swap safe for the coverage test.
        // A sample at radius p only lands inside an item of local thickness w when the pixel sits
        // between p - w and p away from it, so rings at r and r/2 cover every distance in [0, r] as
        // soon as w >= r/2. Anything at least half the stroke radius thick -- every item in practice
        // -- therefore fills exactly the way the box did, and a single ring would have left a gap
        // next to anything thinner than the stroke.
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
                float sampleCoverage = visibleCoverage(sampleUv);
                outer = max(outer, sampleCoverage);
                if (sampleCoverage > 0.0) {
                    nearestDepth = min(nearestDepth, texture(ItemDepthSampler, clamp(sampleUv, vec2(0.0), vec2(1.0))).r);
                }
                if (cubic && sampleCoverage > geometry.w) {
                    // Only the outer ring sits at the stroke's edge, so the inner one votes with less
                    // weight -- the same "nearer samples matter more" bias the box weighting had.
                    faceSum -= sampleDirection * (ring == 0 ? 1.0 : 0.5);
                }
            }
        }
        if (cubic) {
            cubicLight = cubicFaceLight(faceSum);
        }
    }
    float edge = smoothstep(geometry.w, 1.0, outer) * (1.0 - center);
    float visible = 1.0;
    if (sceneOcclusionEnabled()) {
        float sceneDepth = texture(SceneDepthSampler, texCoord).r;
        visible = thirdPersonOcclusion()
            ? (sceneDepth + 0.00015 < nearestDepth ? 0.0 : 1.0)
            : smoothstep(nearestDepth - 0.0004, nearestDepth + 0.0003, sceneDepth);
        visible *= armOcclusion(nearestDepth, texCoord, texel);
        // Third-person occlusion is a binary visibility test. A soft depth
        // transition leaves fringe pixels that bloom into a false closed edge.
        if (sceneDepth + 0.00015 < nearestDepth) {
            discard;
        }
    }
    float alpha = edge * primaryColor.a * visible;
    if (alpha <= 0.001) discard;
    vec3 outlineColor = resolveColor();
    float chromaSafeGain = 1.0 / max(max(outlineColor.r, outlineColor.g), max(outlineColor.b, 0.0001));
    float brightnessGain = min(1.0 + glow * 0.35, chromaSafeGain);
    fragColor = vec4(outlineColor * brightnessGain * cubicLight, alpha);
}
