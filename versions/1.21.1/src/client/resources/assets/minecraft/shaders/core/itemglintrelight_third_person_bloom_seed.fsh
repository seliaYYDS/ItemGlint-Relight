#version 150

// 1.21.1 port of core/third_person_bloom_seed.fsh.
// 1.21.1 is normal-Z only, so the reverse-Z mirror (normalDepth) is gone and the shader needs no
// uniforms at all: it only decides where the bloom may grow.

uniform sampler2D MaskSampler;
uniform sampler2D ItemDepthSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float coverage = texture(MaskSampler, texCoord).a;
    float itemDepth = texture(ItemDepthSampler, texCoord).r;
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    // Same hard depth decision as the third-person outline: no halo through a wall.
    if (coverage <= 0.01 || sceneDepth + 0.00015 < itemDepth) discard;
    fragColor = vec4(itemDepth, 0.0, 0.0, coverage);
}
