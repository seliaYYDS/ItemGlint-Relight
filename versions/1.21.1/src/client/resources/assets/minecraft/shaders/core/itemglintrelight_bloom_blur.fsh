#version 150

// 1.21.1 port of core/held_item_bloom_blur.fsh. The only change is the plain uniform that replaces
// the std140 BlurInfo block; the kernel is unchanged.

uniform sampler2D InputSampler;
uniform vec4 blur; // step x, step y, radius, sample count

in vec2 texCoord;
out vec4 fragColor;

const int MAX_SAMPLES = 6;

void main() {
    int samples = clamp(int(blur.w + 0.5), 1, MAX_SAMPLES);
    float totalWeight = 1.0;
    float alpha = texture(InputSampler, texCoord).a;
    for (int index = 1; index <= MAX_SAMPLES; index++) {
        if (index > samples) break;
        float weight = float(samples + 1 - index);
        vec2 offset = blur.xy * blur.z * float(index) / float(samples);
        alpha += texture(InputSampler, clamp(texCoord + offset, vec2(0.0), vec2(1.0))).a * weight;
        alpha += texture(InputSampler, clamp(texCoord - offset, vec2(0.0), vec2(1.0))).a * weight;
        totalWeight += 2.0 * weight;
    }
    fragColor = vec4(0.0, 0.0, 0.0, alpha / totalWeight);
}
