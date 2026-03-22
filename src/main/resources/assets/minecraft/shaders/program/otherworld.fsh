#version 150

uniform sampler2D DiffuseSampler;

uniform float Strength;      // 里世界强度 (0~1正常, >1过渡闪白)
uniform float Time;           // 累计时间
uniform float PulsePhase;     // 脉动阶段 (用于15秒发光等周期性效果)
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = rand(i);
    float b = rand(i + vec2(1.0, 0.0));
    float c = rand(i + vec2(0.0, 1.0));
    float d = rand(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);

    // ========== 1. 扭曲效果 ==========
    // 缓慢旋转扭曲
    float angle = atan(uv.y - 0.5, uv.x - 0.5);
    float warp = sin(angle * 3.0 + Time * 0.8) * 0.008 * Strength;
    // 波纹扭曲
    float ripple = sin(dist * 20.0 - Time * 2.0) * 0.004 * Strength;
    uv += normalize(uv - center + 0.001) * (warp + ripple);

    // 黑雾飘动扭曲
    float fogWarp = noise(uv * 5.0 + Time * 0.3) * 0.006 * Strength;
    uv.x += fogWarp;
    uv.y += noise(uv * 4.0 - Time * 0.2) * 0.004 * Strength;

    // ========== 2. 色差 ==========
    float aberr = 0.003 * Strength;
    vec3 col;
    col.r = texture(DiffuseSampler, uv + vec2(aberr, 0.0)).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, uv - vec2(aberr, 0.0)).b;

    // ========== 3. 颜色偏移 - 暗紫红色调 ==========
    // 去饱和后重新着色
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    // 里世界色调：暗紫红色
    vec3 otherworldTint = vec3(0.15, 0.02, 0.12);
    vec3 tintedColor = mix(col, vec3(gray) * 0.6 + otherworldTint, Strength * 0.7);

    // ========== 4. 黑雾覆盖 ==========
    float fog = noise(uv * 3.0 + vec2(Time * 0.15, Time * 0.1));
    float fog2 = noise(uv * 6.0 - vec2(Time * 0.2, Time * 0.15));
    float fogMask = (fog * 0.6 + fog2 * 0.4);
    // 边缘雾更浓
    fogMask = fogMask * (0.3 + dist * 0.7);
    tintedColor = mix(tintedColor, otherworldTint * 0.3, fogMask * Strength * 0.4);

    // ========== 5. 脉动效果 ==========
    float pulse = sin(Time * 1.5) * 0.5 + 0.5;
    tintedColor *= 1.0 - pulse * 0.08 * Strength;

    // ========== 6. 暗角 ==========
    float vignette = smoothstep(1.0, 0.2, dist);
    tintedColor *= mix(1.0, vignette, Strength * 0.6);

    // ========== 7. 颗粒噪声 ==========
    float grain = (rand(uv + Time * 0.1) - 0.5) * 0.06 * Strength;
    tintedColor += grain;

    // ========== 8. 整体暗化 ==========
    tintedColor *= mix(1.0, 0.7, clamp(Strength, 0.0, 1.0));

    // ========== 9. 过渡闪白效果 (Strength > 1.0 时) ==========
    if (Strength > 1.0) {
        float flashIntensity = clamp(Strength - 1.0, 0.0, 1.0);
        tintedColor = mix(tintedColor, vec3(1.0), flashIntensity);
    }

    fragColor = vec4(tintedColor, 1.0);
}
