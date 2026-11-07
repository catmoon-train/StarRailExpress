#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float SettlementTime;
uniform float SettlementBlur;
in vec2 texCoord;
out vec4 fragColor;

vec3 sampleColor(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, 0.001, 0.999)).rgb;
}

void main() {
    vec2 pixel = vec2(max(1.0, SettlementBlur)) / InSize;
    vec3 original = sampleColor(texCoord);
    vec3 left = sampleColor(texCoord - vec2(pixel.x, 0.0));
    vec3 right = sampleColor(texCoord + vec2(pixel.x, 0.0));
    vec3 up = sampleColor(texCoord + vec2(0.0, pixel.y));
    vec3 down = sampleColor(texCoord - vec2(0.0, pixel.y));
    vec3 soft = original * 0.4 + (left + right + up + down) * 0.15;
    vec3 color = mix(original, soft, clamp(SettlementBlur / 2.0, 0.0, 1.0));

    // 只轻度收敛色彩与明暗，玩家皮肤和扩展布景仍保留自己的颜色。
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float stepped = floor(luma * 7.0 + 0.5) / 7.0;
    color *= mix(luma, stepped, 0.16) / max(0.025, luma);
    color = mix(vec3(luma), color, 0.88);
    float edge = max(length(right - left), length(up - down));
    float ink = smoothstep(0.24, 0.72, edge) * 0.32 * smoothstep(0.4, 2.0, SettlementTime);
    color *= 1.0 - ink;

    // 稳定的印刷网点只进入暗部；没有随机噪点、色散或全屏黑白化。
    vec2 cell = fract(gl_FragCoord.xy / max(3.0, InSize.y / 180.0)) - 0.5;
    float dotInk = 1.0 - smoothstep(0.11, 0.25, length(cell));
    float shadow = 1.0 - smoothstep(0.13, 0.40, luma);
    color *= 1.0 - dotInk * shadow * 0.09;
    color *= vec3(1.018, 1.0, 0.975);
    vec2 centered = texCoord * 2.0 - 1.0;
    color *= 1.0 - smoothstep(0.50, 1.75, dot(centered, centered)) * 0.16;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
