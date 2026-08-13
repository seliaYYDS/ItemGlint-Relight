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
};

in vec2 texCoord;
out vec4 fragColor;

const int MAX_OUTLINE_SAMPLES = 48;
const float TAU = 6.28318530718;

float coverage(vec2 uv) {
    return texture(MaskSampler, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

// Neighbor samples are potential outline sources. Do not let an item sample
// hidden by the scene contribute to the outer shell at an occluder boundary.
float visibleCoverage(vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(1.0));
    float sampleCoverage = texture(MaskSampler, clamped).a;
    if (sampleCoverage <= 0.0 || scrollBounds.z >= 0.5) {
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
    if (cubicMode()) {
        int cubicRadius = max(1, int(floor(geometry.z + 0.5)));
        vec2 faceSum = vec2(0.0);
        float lightWeight = 0.0;
        for (int y = -cubicRadius; y <= cubicRadius; y++) {
            for (int x = -cubicRadius; x <= cubicRadius; x++) {
                ivec2 samplePixel = clamp(centerPixel + ivec2(x, y), ivec2(0), maskSize - ivec2(1));
                vec2 sampleUv = (vec2(samplePixel) + 0.5) / vec2(maskSize);
                float sampleCoverage = visibleCoverage(sampleUv);
                if (sampleCoverage > geometry.w) {
                    float layer = 1.0 - float(max(abs(x), abs(y))) / float(cubicRadius + 1);
                    float weight = 0.20 + 0.80 * layer;
                    faceSum += vec2(float(-x), float(-y)) * weight;
                    lightWeight += weight;
                    nearestDepth = min(nearestDepth, texture(ItemDepthSampler, sampleUv).r);
                }
                outer = max(outer, sampleCoverage);
            }
        }
        if (lightWeight > 0.0001) {
            vec2 face = normalize(faceSum);
            float horizontalLight = face.x < 0.0 ? 0.96 : 0.72;
            float verticalLight = face.y < 0.0 ? 1.14 : 0.64;
            float faceDelta = abs(face.x) - abs(face.y);
            float seam = max(fwidth(faceDelta) * 2.5, 0.055);
            cubicLight = mix(verticalLight, horizontalLight, smoothstep(-seam, seam, faceDelta));
        }
    } else if (softness <= 0.001) {
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
        int sampleCount = outlineSampleCount();
        int ringCount = softness >= 0.999 ? 2 : 1;
        for (int i = 0; i < MAX_OUTLINE_SAMPLES; i++) {
            if (i >= sampleCount) break;
            float angle = TAU * float(i) / float(sampleCount);
            vec2 circleDirection = vec2(cos(angle), sin(angle));
            vec2 squareDirection = circleDirection / max(max(abs(circleDirection.x), abs(circleDirection.y)), 0.0001);
            vec2 sampleDirection = mix(squareDirection, circleDirection, softness);
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
            }
        }
    }
    float edge = smoothstep(geometry.w, 1.0, outer) * (1.0 - center);
    float visible = 1.0;
    if (scrollBounds.z < 0.5) {
        float sceneDepth = texture(SceneDepthSampler, texCoord).r;
        visible = smoothstep(nearestDepth - 0.0004, nearestDepth + 0.0003, sceneDepth);
        float armCoverage = texture(ArmOccluderSampler, texCoord).a;
        float armDepth = texture(ArmOccluderDepthSampler, texCoord).r;
        visible *= mix(1.0, 1.0 - smoothstep(armDepth - 0.0003, armDepth + 0.0003, nearestDepth), armCoverage);
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
