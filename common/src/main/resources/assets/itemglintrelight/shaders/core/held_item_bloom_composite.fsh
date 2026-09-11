#version 330

uniform sampler2D BloomSampler;
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

bool thirdPersonOcclusion() {
    return scrollBounds.z > 1.5;
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
    // A blur kernel's raw coverage peaks well below 1 outside the item, so normalize it before
    // shaping alpha. The exponent then creates a visible transparency gradient over the whole
    // halo, instead of merely making the last few edge pixels blurrier.
    float normalizedCoverage = clamp(coverage / 0.30, 0.0, 1.0);
    float alphaExponent = 0.25 + 1.35 * softness;
    return pow(normalizedCoverage, alphaExponent);
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
    return clamp(1.05 + geometry.z * 0.46 + clamp(animation.w, 0.0, 1.0) * 0.32, 0.95, 4.8);
}

// Coverage owns the decision, depth only refines it: an outline pixel always sits next to the
// silhouette, so the item depth there is the nearest item surface in the stroke's reach while the
// arm would only be a single texel. See the outline shader for the full reasoning.
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

    float depthVisibility = smoothstep(itemDepth - 0.0035, itemDepth + 0.0008, armDepth);
    float depthContribution = mix(1.0, depthVisibility, (0.18 + 0.08 * smoothstep(0.18, 0.90, armCoverage)) * validDepth);
    return min(maskVisibility, depthContribution);
}

void main() {
    float blurredCoverage = max(texture(BloomSampler, texCoord).a - texture(MaskSampler, texCoord).a, 0.0);
    float bloom = bloomAlpha(blurredCoverage);
    vec2 texel = 1.0 / max(geometry.xy, vec2(1.0));
    float itemDepth = 1.0;
    bool hasVisibleItem = false;
    bool thirdPerson = thirdPersonOcclusion();
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 sampleUv = clamp(texCoord + vec2(x, y) * texel, vec2(0.0), vec2(1.0));
            // Iris third-person replay uses a blank depth attachment. Empty mask pixels must
            // not become fake item-depth sources, while the other paths keep the legacy bloom
            // sampling behavior unchanged.
            if (thirdPerson && texture(MaskSampler, sampleUv).a <= 0.01) {
                continue;
            }
            float sampleItemDepth = texture(ItemDepthSampler, sampleUv).r;
            // depthtex2 is the authoritative Iris scene depth, but it is generated in a
            // different pass from the replay target. Use it only for the final pixel clip:
            // comparing it here would reject every bloom source on small depth differences.
            if (thirdPerson || texture(SceneDepthSampler, sampleUv).r + 0.00015 >= sampleItemDepth) {
                itemDepth = min(itemDepth, sampleItemDepth);
                hasVisibleItem = true;
            }
        }
    }
    if (!hasVisibleItem) {
        discard;
    }
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    float visible = thirdPerson
        ? (sceneDepth + 0.00015 < itemDepth ? 0.0 : 1.0)
        : smoothstep(itemDepth - 0.0004, itemDepth + 0.0003, sceneDepth);
    visible *= armOcclusion(itemDepth, texCoord, texel);
    if (sceneDepth + 0.00015 < itemDepth) {
        discard;
    }
    // Bloom intensity owns bloom alpha independently of outline opacity: intensity 1 is fully
    // opaque wherever the softness curve has not reduced it.
    float alpha = bloom * effect.w * visible;
    if (alpha <= 0.001) discard;
    fragColor = vec4(resolveColor(), min(alpha, 1.0));
}
