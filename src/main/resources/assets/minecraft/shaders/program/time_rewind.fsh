#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength; // 0..1 effect intensity
uniform float Time;     // accumulated time

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(0.5, 0.5);
    vec2 toCenter = uv - center;
    float dist = length(toCenter);

    // 时间回溯漩涡：随时间旋转、随强度增强的螺旋扭曲
    float swirl = (1.0 - dist) * Strength * 1.6;
    float ang = swirl * sin(Time * 2.0) * 0.9;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 swirled = vec2(
        toCenter.x * ca - toCenter.y * sa,
        toCenter.x * sa + toCenter.y * ca
    ) + center;

    // 波动 + 径向脉冲，营造时间扭曲感
    float wave = sin(dist * 40.0 - Time * 6.0) * 0.004 * Strength;
    vec2 distortUv = swirled + normalize(toCenter + 0.0001) * wave;

    // 色差（chromatic aberration）随强度增大
    float ab = 0.006 * Strength * (0.6 + 0.4 * sin(Time * 3.0));
    vec2 dir = normalize(toCenter + 0.0001);
    float r = texture(DiffuseSampler, distortUv + dir * ab).r;
    float g = texture(DiffuseSampler, distortUv).g;
    float b = texture(DiffuseSampler, distortUv - dir * ab).b;
    vec3 col = vec3(r, g, b);

    // 紫青色调 + 轻微去饱和，呈现“恍惚/时空”滤镜
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 tint = mix(vec3(gray), vec3(0.45, 0.35, 0.85), 0.5);
    vec3 dazed = mix(col, tint, 0.45);

    // 暗角，强化漩涡中心
    float vignette = smoothstep(0.85, 0.2, dist);
    dazed *= mix(1.0, vignette, Strength * 0.6);

    vec4 base = texture(DiffuseSampler, uv);
    vec3 finalColor = mix(base.rgb, dazed, clamp(Strength, 0.0, 1.0));
    fragColor = vec4(finalColor, base.a);
}
