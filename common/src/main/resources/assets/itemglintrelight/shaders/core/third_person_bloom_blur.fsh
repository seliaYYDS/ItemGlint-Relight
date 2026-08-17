#version 330

uniform sampler2D InputSampler;

layout(std140) uniform BlurInfo {
    vec4 blur;
};

in vec2 texCoord;
out vec4 fragColor;

const int MAX_SAMPLES = 6;

void accumulate(vec2 uv, float weight, inout float alpha, inout float totalWeight, inout float nearestDepth) {
    vec4 sampleValue = texture(InputSampler, clamp(uv, vec2(0.0), vec2(1.0)));
    alpha += sampleValue.a * weight;
    totalWeight += weight;
    if (sampleValue.a > 0.001) nearestDepth = min(nearestDepth, sampleValue.r);
}

void main() {
    int samples = clamp(int(blur.w + 0.5), 1, MAX_SAMPLES);
    float alpha = 0.0;
    float totalWeight = 0.0;
    float nearestDepth = 1.0;
    accumulate(texCoord, 1.0, alpha, totalWeight, nearestDepth);
    for (int index = 1; index <= MAX_SAMPLES; index++) {
        if (index > samples) break;
        float weight = float(samples + 1 - index);
        vec2 offset = blur.xy * blur.z * float(index) / float(samples);
        accumulate(texCoord + offset, weight, alpha, totalWeight, nearestDepth);
        accumulate(texCoord - offset, weight, alpha, totalWeight, nearestDepth);
    }
    fragColor = vec4(nearestDepth, 0.0, 0.0, alpha / max(totalWeight, 1.0));
}
