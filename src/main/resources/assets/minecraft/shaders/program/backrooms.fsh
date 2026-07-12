#version 150

// 后室滤镜（BACKROOMS_FILTER 驱动的纯视觉后处理）
// 搬运自 MinecraftFoundFootage(spb-revamped) 的 VHS 后处理管线：
//   vhs_post.fsh —— 桶形畸变 / YUV 亮度噪声 / 白噪点 / VHS 色度噪声
//   glitch.fsh   —— 磁带行抖动与色差
//   common.glsl  —— hash12 / octave / rgb2yuv / yuv2rgb
// 原实现基于 Veil 延迟管线，此处改写为原版 PostPass 单趟后处理。

uniform sampler2D DiffuseSampler;
uniform sampler2D VhsNoiseSampler;

uniform float Strength;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

// 对应 vhs_post 中的 DistortionStrength uniform，此处为常量
const float DISTORTION_STRENGTH = 0.65;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float octave(float x) {
    return mod(sin(x * 2.0) * sin(x * 4.0) * sin(x * 32.0), 1.0);
}

vec3 rgb2yuv(vec3 rgb) {
    float y = 0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b;
    return vec3(y, 0.493 * (rgb.b - y), 0.877 * (rgb.r - y));
}

vec3 yuv2rgb(vec3 yuv) {
    float y = yuv.x;
    float u = yuv.y;
    float v = yuv.z;
    return vec3(
        y + 1.0 / 0.877 * v,
        y - 0.39393 * u - 0.58081 * v,
        y + 1.0 / 0.493 * u
    );
}

vec2 barrelDistortion(vec2 uv) {
    vec2 pos = 2.0 * uv - 1.0;
    float len = (distance(pos, vec2(0.0)) / 1.5) * DISTORTION_STRENGTH;
    pos = pos + pos * len * len;
    return 0.5 * (pos + 1.0);
}

void main() {
    float s = clamp(Strength, 0.0, 1.0);

    // 桶形畸变（按强度过渡，避免生效瞬间画面跳变）
    vec2 uv = mix(texCoord, barrelDistortion(texCoord), s);

    // 磁带行抖动
    uv.x += octave(uv.y + Time * 1.7) * 0.0025 * s;

    // 色差分离采样
    float chromAbb = 0.0015 * s;
    vec3 col;
    col.r = texture(DiffuseSampler, uv + chromAbb).r;
    col.g = texture(DiffuseSampler, uv - chromAbb).g;
    col.b = texture(DiffuseSampler, uv).b;

    // VHS 后期：YUV 空间的亮度噪声 + 白噪点划痕 + 色度噪声
    vec3 yuv = rgb2yuv(col);
    float n1 = hash12(uv * 260.23535 + Time * 7.0);
    float n2 = hash12(uv * 737.36346 + Time * 10.0);
    yuv += (yuv * vec3((n1 + n2) * 2.0 - 1.0)) * 0.05 * s;
    yuv.x += step(0.99994, n1) * 10.0 * s;
    vec2 vhsNoise = texture(VhsNoiseSampler, fract(vec2(uv.x - Time * 2.5, uv.y + Time * 4.2))).gb * 0.1;
    yuv.yz += vhsNoise * 0.9 * 0.2 * s;
    col = yuv2rgb(yuv);

    // 畸变出界处涂黑（摄像机取景框圆角）
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        col = vec3(0.0);
    }

    // 暗角
    float dist = distance(uv, vec2(0.5));
    col *= mix(1.0, smoothstep(0.95, 0.4, dist), 0.45 * s);

    fragColor = vec4(col, 1.0);
}
