#version 330
#extension GL_ARB_separate_shader_objects : require


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
    vec4 scrollMode;
    vec4 scrollBounds;
    vec4 materialPalette[8];
    vec4 bloomParameters;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// 26.2 renders with reverse-Z: glClipControl(GL_LOWER_LEFT, GL_ZERO_TO_ONE) plus a reversed
// projection puts the near plane at 1.0 and the far plane at 0.0, and every vanilla pipeline
// compares with GREATER_THAN_OR_EQUAL. The hard depth decision below is written for the 1.21.11
// convention (near 0.0, far 1.0), so sampled depth is mirrored back at the sampling boundary. The
// mapping is exact -- a reversed projection stores exactly 1 - z -- and normalDepth is the identity
// unless OutlineInfo.bloomParameters.z reports reverse-Z, which only 26.2 sets.
float normalDepth(float depth) {
    return bloomParameters.z > 0.5 ? 1.0 - depth : depth;
}

void main() {
    float coverage = texture(MaskSampler, texCoord).a;
    // The depth this pass *stores* stays raw: third_person_bloom_composite mirrors it again when it
    // reads the seed back, exactly like it mirrors the scene depth.
    float rawItemDepth = texture(ItemDepthSampler, texCoord).r;
    // Match the third-person outline's hard depth decision. Iris supplies depthtex2 here.
    float itemDepth = normalDepth(rawItemDepth);
    float sceneDepth = normalDepth(texture(SceneDepthSampler, texCoord).r);
    if (coverage <= 0.01 || sceneDepth + 0.00015 < itemDepth) discard;
    fragColor = vec4(rawItemDepth, 0.0, 0.0, coverage);
}
