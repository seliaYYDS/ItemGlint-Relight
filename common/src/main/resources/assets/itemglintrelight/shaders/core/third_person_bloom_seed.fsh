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
    if (coverage <= 0.01 || sceneDepth + 0.00015 < itemDepth) discard;
    fragColor = vec4(itemDepth, 0.0, 0.0, coverage);
}
