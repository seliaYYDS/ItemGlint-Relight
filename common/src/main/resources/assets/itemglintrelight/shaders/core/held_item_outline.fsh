#version 330

uniform sampler2D MaskSampler;
uniform sampler2D ItemDepthSampler;
uniform sampler2D SceneDepthSampler;

layout(std140) uniform OutlineInfo {
    vec4 primaryColor;
    vec4 secondaryColor;
    vec4 geometry;
    vec4 animation;
    vec4 effect;
    vec4 colorScroll;
    vec4 materialPalette[8];
};

in vec2 texCoord;
out vec4 fragColor;

const int MAX_OUTLINE_SAMPLES = 32;
const float TAU = 6.28318530718;

float coverage(vec2 uv) {
    return texture(MaskSampler, clamp(uv, vec2(0.0), vec2(1.0))).a;
}

vec3 paletteColor(int index) {
    return materialPalette[clamp(index, 0, 7)].rgb;
}

float scrollPosition() {
    float normalizedInterval = clamp((colorScroll.z - 0.25) / 1.25, 0.0, 1.0);
    float segmentPixels = mix(72.0, 384.0, normalizedInterval);
    return dot(texCoord * geometry.xy, colorScroll.xy) / segmentPixels - animation.y * animation.z * 0.12;
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
    float glow = glowIntensity();
    if (softness <= 0.001) {
        int sharpRadius = max(1, int(floor(geometry.z + 0.5)));
        for (int y = -sharpRadius; y <= sharpRadius; y++) {
            for (int x = -sharpRadius; x <= sharpRadius; x++) {
                ivec2 samplePixel = clamp(centerPixel + ivec2(x, y), ivec2(0), maskSize - ivec2(1));
                outer = max(outer, texelFetch(MaskSampler, samplePixel, 0).a);
                nearestDepth = min(nearestDepth, texelFetch(ItemDepthSampler, samplePixel, 0).r);
            }
        }
    } else {
        int sampleCount = outlineSampleCount();
        for (int i = 0; i < MAX_OUTLINE_SAMPLES; i++) {
            if (i >= sampleCount) break;
            float angle = TAU * float(i) / float(sampleCount);
            vec2 circleDirection = vec2(cos(angle), sin(angle));
            vec2 squareDirection = circleDirection / max(max(abs(circleDirection.x), abs(circleDirection.y)), 0.0001);
            vec2 sampleDirection = mix(squareDirection, circleDirection, softness);
            vec2 sampleUv = texCoord + sampleDirection * texel * geometry.z;
            outer = max(outer, coverage(sampleUv));
            nearestDepth = min(nearestDepth, texture(ItemDepthSampler, clamp(sampleUv, vec2(0.0), vec2(1.0))).r);
        }
    }
    float edge = smoothstep(geometry.w, 1.0, outer) * (1.0 - center);
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    float visible = smoothstep(nearestDepth - 0.0004, nearestDepth + 0.0003, sceneDepth);
    float alpha = edge * primaryColor.a * visible;
    if (alpha <= 0.001) discard;
    vec3 outlineColor = resolveColor();
    float chromaSafeGain = 1.0 / max(max(outlineColor.r, outlineColor.g), max(outlineColor.b, 0.0001));
    float brightnessGain = min(1.0 + glow * 0.35, chromaSafeGain);
    fragColor = vec4(outlineColor * brightnessGain, alpha);
}
