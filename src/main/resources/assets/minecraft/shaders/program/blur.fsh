#version 150

uniform sampler2D DiffuseSampler;
uniform float BlurStrength;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    if (BlurStrength <= 0.0) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    // 简单的盒式模糊
    vec4 sum = vec4(0.0);
    float strength = BlurStrength * 0.01;

    // 如果理智非常低，添加脉动效果
    float pulse = 1.0;
    if (BlurStrength > 0.5) {
        pulse = 1.0 + sin(Time * 3.0) * 0.3;
        strength *= pulse;
    }

    // 采样周围像素
    sum += texture(DiffuseSampler, vec2(texCoord.x - 4.0 * strength, texCoord.y)) * 0.05;
    sum += texture(DiffuseSampler, vec2(texCoord.x - 3.0 * strength, texCoord.y)) * 0.09;
    sum += texture(DiffuseSampler, vec2(texCoord.x - 2.0 * strength, texCoord.y)) * 0.12;
    sum += texture(DiffuseSampler, vec2(texCoord.x - strength, texCoord.y)) * 0.15;
    sum += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y)) * 0.16;
    sum += texture(DiffuseSampler, vec2(texCoord.x + strength, texCoord.y)) * 0.15;
    sum += texture(DiffuseSampler, vec2(texCoord.x + 2.0 * strength, texCoord.y)) * 0.12;
    sum += texture(DiffuseSampler, vec2(texCoord.x + 3.0 * strength, texCoord.y)) * 0.09;
    sum += texture(DiffuseSampler, vec2(texCoord.x + 4.0 * strength, texCoord.y)) * 0.05;

    // 垂直模糊
    vec4 sum2 = vec4(0.0);
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y - 4.0 * strength)) * 0.05;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y - 3.0 * strength)) * 0.09;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y - 2.0 * strength)) * 0.12;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y - strength)) * 0.15;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y)) * 0.16;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y + strength)) * 0.15;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y + 2.0 * strength)) * 0.12;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y + 3.0 * strength)) * 0.09;
    sum2 += texture(DiffuseSampler, vec2(texCoord.x, texCoord.y + 4.0 * strength)) * 0.05;

    // 混合水平和垂直模糊
    fragColor = (sum + sum2) * 0.5;
}