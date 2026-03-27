#version 150

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float GameTime;
uniform float Intensity;
uniform float DistortionStrength;
uniform float ChromaticAberration;
uniform float FlickerSpeed;
uniform float ScanlineStrength;
uniform float RedStrength;

in vec2 texCoord;
out vec4 fragColor;

// 随机函数
float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

// 噪声函数
float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    vec2 uv = texCoord;
    float t = GameTime * 3.0;

    // RGB分离（色差效果进一步减弱）
    float chroma = ChromaticAberration * Intensity * 0.18; // 再次降低色差强度
    float r = texture(DiffuseSampler, uv + vec2(chroma, 0.0)).r;
    float g = texture(DiffuseSampler, uv + vec2(0.0, chroma * 0.18)).g;
    float b = texture(DiffuseSampler, uv + vec2(-chroma * 0.18, 0.0)).b;
    float a = texture(DiffuseSampler, uv).a;

    // 去除闪烁效果，直接使用原色
    vec3 color = vec3(r, g, b);

    // 饱和度增强减弱
    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luminance), color, 1.0 + Intensity * 0.18);



    // 添加随机噪点（高强度时，强度减弱）
    if (Intensity > 0.5) {
        float noiseAmount = 0.02 * (Intensity - 0.5) * 1.2;
        color += (random(uv + t) - 0.5) * noiseAmount;
    }

    // 边缘光晕（高强度时，减弱）
    if (Intensity > 0.7) {
        vec2 center = vec2(0.5, 0.5);
        float dist = distance(uv, center);
        float glow = 1.0 - smoothstep(0.0, 0.7, dist);

        // 彩色光晕
        vec3 glowColor = vec3(
            0.9 + 0.1 * sin(t),
            0.4 + 0.1 * sin(t * 1.5 + 2.0),
            0.9 + 0.1 * sin(t * 2.0 + 4.0)
        );

        color = mix(color, glowColor, glow * 0.13 * (Intensity - 0.7) * 2.0);
    }

    // 对比度调整减弱
    float contrast = 1.0 + Intensity * 0.12;
    color = (color - 0.5) * contrast + 0.5;

    // 色调偏移（高强度时，减弱）
    if (Intensity > 0.6) {
        float hueShift = sin(t * 3.0) * 0.13 * (Intensity - 0.6) * 1.2;
        // 简单的色调偏移
        color = vec3(
            color.r * (0.9 + 0.1 * sin(hueShift)),
            color.g * (0.9 + 0.1 * sin(hueShift + 2.094)),
            color.b * (0.9 + 0.1 * sin(hueShift + 4.188))
        );
    }

    // 叠加红色滤镜，提升透明度（最大混合度降低）
    vec3 redLayer = vec3(1.0, 0.08, 0.08);
    float redMix = smoothstep(0.0, 1.0, RedStrength) * 0.45; // 最大混合度45%
    vec3 finalColor = mix(color, redLayer, redMix);

    // 最终颜色
    fragColor = vec4(finalColor, a);
}