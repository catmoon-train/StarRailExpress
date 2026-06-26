#version 150

uniform sampler2D DiffuseSampler;

uniform float Strength;
uniform float TimeTotal;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    vec3 col = texture(DiffuseSampler, uv).rgb;

    // 转为灰度
    float gray = dot(col, vec3(0.299, 0.587, 0.114));

    // 轻微冷色调，营造里世界冰冷氛围
    vec3 tinted = vec3(gray) * vec3(0.92, 0.96, 1.0);

    // 细微颗粒噪声
    float grain = (rand(uv + TimeTotal) - 0.5) * 0.06;
    tinted += grain;

    // 暗角
    float dist = distance(uv, vec2(0.5));
    float vignette = smoothstep(0.95, 0.35, dist);
    tinted *= mix(1.0, vignette, 0.6);

    // 按强度混合彩色原图与灰白特效
    float s = clamp(Strength, 0.0, 1.0);
    vec3 finalColor = mix(col, tinted, s);

    fragColor = vec4(finalColor, 1.0);
}
