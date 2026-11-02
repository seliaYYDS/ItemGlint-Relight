#version 150

// 1.21.1 port of core/third_person_bloom_composite.fsh.
// Differences: plain uniforms (no std140 block) and no reverse-Z mirror; everything else matches.

uniform sampler2D BloomSampler;
uniform sampler2D SeedSampler;
uniform sampler2D SceneDepthSampler;

uniform vec4 primaryColor;
uniform vec4 secondaryColor;
uniform vec4 geometry;
uniform vec4 animation;
uniform vec4 effect;
uniform vec4 colorScroll;
uniform vec4 scrollMode;
uniform vec4 scrollBounds;
uniform vec4 bloomParameters;
uniform vec4 clipRect;
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

const float TAU = 6.28318530718;

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
    if (paletteSize < 1.5) return paletteColor(0);
    float segment = floor(position);
    int first = int(mod(segment, paletteSize));
    int second = int(mod(segment + 1.0, paletteSize));
    return paletteBlend(paletteColor(first), paletteColor(second), smoothRamp(fract(position)));
}

float bloomAlpha(float coverage) {
    float softness = clamp(bloomParameters.x, 0.0, 1.0);
    float hardCoverage = coverage > 0.001 ? 1.0 : 0.0;
    if (softness <= 0.001) return hardCoverage;
    float normalizedCoverage = clamp(coverage / 0.30, 0.0, 1.0);
    float alphaExponent = 0.25 + 1.35 * softness;
    return pow(normalizedCoverage, alphaExponent);
}

bool outsideClipRect() {
    return clipRect.z > 0.0
            && (gl_FragCoord.x < clipRect.x || gl_FragCoord.y < clipRect.y
                || gl_FragCoord.x >= clipRect.x + clipRect.z || gl_FragCoord.y >= clipRect.y + clipRect.w);
}

void main() {
    if (outsideClipRect()) {
        discard;
    }
    vec4 bloomSample = texture(BloomSampler, texCoord);
    float blurredCoverage = max(bloomSample.a - texture(SeedSampler, texCoord).a, 0.0);
    if (blurredCoverage <= 0.001) discard;
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (sceneDepth + 0.00015 < bloomSample.r) discard;
    float alpha = bloomAlpha(blurredCoverage) * effect.w;
    if (alpha <= 0.001) discard;
    fragColor = vec4(resolveColor(), min(alpha, 1.0));
}
