#version 330

uniform sampler2D BloomSampler;
uniform sampler2D SeedSampler;
uniform sampler2D SceneDepthSampler;

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

float scrollPosition() {
    float normalizedInterval = clamp((colorScroll.z - 0.25) / 1.25, 0.0, 1.0);
    float segmentPixels = mix(72.0, 384.0, normalizedInterval);
    float timeOffset = animation.y * animation.z * 0.12;
    if (scrollMode.x < 0.5) {
        return dot(texCoord * geometry.xy * max(scrollBounds.w, 1.0), colorScroll.xy) / segmentPixels - timeOffset;
    }
    vec2 relative = (texCoord * geometry.xy - scrollMode.yz) / max(scrollBounds.xy, vec2(1.0));
    float angle = atan(relative.y, relative.x) - atan(colorScroll.y, colorScroll.x);
    float perimeter = 6.28318530718 * max(scrollMode.w, 1.0) * max(scrollBounds.w, 1.0);
    float cycles = max(1.0, round(perimeter / (segmentPixels * 2.0)));
    return (angle / 6.28318530718 + 0.5) * cycles * 2.0 - timeOffset;
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

vec3 resolveColor() {
    float mode = animation.x;
    if (mode < 0.5) return primaryColor.rgb;
    float position = scrollPosition();
    if (mode < 1.5) {
        float segment = floor(position);
        return mod(segment, 2.0) < 1.0
            ? paletteBlend(primaryColor.rgb, secondaryColor.rgb, smoothRamp(fract(position)))
            : paletteBlend(secondaryColor.rgb, primaryColor.rgb, smoothRamp(fract(position)));
    }
    if (mode < 2.5) return hsvToRgb(fract(position / 6.0), 0.88, 1.0);
    float paletteSize = max(effect.z, 1.0);
    if (paletteSize < 1.5) return materialPalette[0].rgb;
    float segment = floor(position);
    int first = int(mod(segment, paletteSize));
    int second = int(mod(segment + 1.0, paletteSize));
    return paletteBlend(materialPalette[clamp(first, 0, 7)].rgb, materialPalette[clamp(second, 0, 7)].rgb,
            smoothRamp(fract(position)));
}

float bloomAlpha(float coverage) {
    float softness = clamp(bloomParameters.x, 0.0, 1.0);
    float hardCoverage = coverage > 0.001 ? 1.0 : 0.0;
    if (softness <= 0.001) return hardCoverage;
    float normalizedCoverage = clamp(coverage / 0.30, 0.0, 1.0);
    float alphaExponent = 0.25 + 1.35 * softness;
    return pow(normalizedCoverage, alphaExponent);
}

void main() {
    vec4 bloomSample = texture(BloomSampler, texCoord);
    float blurredCoverage = max(bloomSample.a - texture(SeedSampler, texCoord).a, 0.0);
    if (blurredCoverage <= 0.001) discard;
    // The propagated depth belongs to a visible seed. Reject only when current scene geometry
    // is in front, exactly like the third-person outline's Iris-aware depth comparison.
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (sceneDepth + 0.00015 < bloomSample.r) discard;
    float alpha = bloomAlpha(blurredCoverage) * effect.w;
    if (alpha <= 0.001) discard;
    fragColor = vec4(resolveColor(), min(alpha, 1.0));
}
