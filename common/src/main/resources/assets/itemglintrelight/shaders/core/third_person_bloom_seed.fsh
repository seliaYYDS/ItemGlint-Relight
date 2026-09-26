#version 330

uniform sampler2D MaskSampler;
uniform sampler2D ItemDepthSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float coverage = texture(MaskSampler, texCoord).a;
    float itemDepth = texture(ItemDepthSampler, texCoord).r;
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    // Match the third-person outline's hard depth decision. Iris supplies depthtex2 here.
    // TODO(26.2 third person): this comparison is still the 1.21.11 normal-Z form, and this shader
    // has no OutlineInfo uniform to carry the reverse-Z flag the other composites use. The stored
    // depth stays raw either way, so third_person_bloom_composite already mirrors it on read; only
    // this gate needs the flag once the third-person pass is wired up on 26.2.
    if (coverage <= 0.01 || sceneDepth + 0.00015 < itemDepth) discard;
    fragColor = vec4(itemDepth, 0.0, 0.0, coverage);
}
