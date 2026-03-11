#version 150

uniform sampler2D DiffuseSampler;

uniform float TimeProgress;
uniform float StopAmount;
uniform float TimeTotal;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);

    // 时间波纹（扭曲）
    float ripple = sin(dist * 30.0 - TimeProgress * 15.0) * 0.015 * StopAmount;
    uv += normalize(uv - center) * ripple;

    // 微抖动
    float jitter = (rand(uv + TimeTotal) - 0.5) * 0.005 * StopAmount;
    uv += jitter;

    // 色差
    float aberr = 0.002 * StopAmount;
    vec3 col;
    col.r = texture(DiffuseSampler, uv + vec2(aberr, 0.0)).r;
    col.g = texture(DiffuseSampler, uv).g;
    col.b = texture(DiffuseSampler, uv - vec2(aberr, 0.0)).b;

    // 转为灰度
    float gray = dot(col, vec3(0.299, 0.587, 0.114));

    // 颗粒噪声
    float grain = (rand(vec2(uv.x + TimeProgress, uv.y - TimeProgress)) - 0.5) * 0.12 * StopAmount;
    gray += grain;

    // 暗角
    float vignette = smoothstep(0.9, 0.3, dist);
    gray *= vignette;

    // 根据 StopAmount 混合彩色原图与黑白特效
    vec3 finalColor = mix(col, vec3(gray), StopAmount);

    fragColor = vec4(finalColor, 1.0);
}