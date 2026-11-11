#version 150

uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
uniform float Pressure;
uniform float Condemned;
uniform float Entry;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float grainHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float strength = clamp(Strength, 0.0, 1.0);
    float pressure = clamp(Pressure, 0.0, 1.0);
    float condemned = clamp(Condemned, 0.0, 1.0);
    float entry = clamp(Entry, 0.0, 1.0);
    vec2 pixel = 1.0 / max(OutSize, vec2(1.0));
    vec2 centered = (texCoord - 0.5) * vec2(1.0, 1.1);
    float edge = smoothstep(0.30, 0.72, length(centered));
    float breath = 0.5 + 0.5 * sin(Time * (1.6 + pressure * 0.8));

    // 扭曲只在外围，位移每轴最多 0.9 像素；不移动准星附近的场景。
    vec2 warp = vec2(sin(texCoord.y * 32.0 + Time * 0.4),
                     sin(texCoord.x * 27.0 - Time * 0.32));
    vec2 uv = clamp(texCoord + warp * pixel * 0.9 * edge * strength * (0.2 + pressure * 0.8),
                    pixel * 0.5, vec2(1.0) - pixel * 0.5);
    vec3 color = texture(DiffuseSampler, uv).rgb;

    // 中央仅轻微去饱和/压暗，保留路线、玩家和手持物的辨识度。
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(luma), strength * 0.12);
    color *= mix(vec3(1.0), vec3(1.0, 0.975, 0.97), strength);
    float darkness = 0.05 + edge * (0.13 + pressure * 0.08 + breath * 0.035
                                   + condemned * 0.04 + entry * 0.035);
    color *= 1.0 - strength * darkness;

    // 缓慢漂移的暗红裂纹限于屏幕外沿，不使用白闪或快速明暗跳变。
    float sideSeam = 0.045 + sin(texCoord.y * 21.0 + Time * 0.12) * 0.012
                           + sin(texCoord.y * 97.0) * 0.004;
    float topSeam = 0.038 + sin(texCoord.x * 24.0 - Time * 0.10) * 0.009
                          + sin(texCoord.x * 113.0) * 0.003;
    float sideWidth = max(pixel.x, 0.0007);
    float topWidth = max(pixel.y, 0.0007);
    float sideCrack = 1.0 - smoothstep(sideWidth, sideWidth * 3.0,
            abs(min(texCoord.x, 1.0 - texCoord.x) - sideSeam));
    float topCrack = 1.0 - smoothstep(topWidth, topWidth * 3.0,
            abs(min(texCoord.y, 1.0 - texCoord.y) - topSeam));
    float cracks = max(sideCrack, topCrack * 0.75);
    color += vec3(0.28, 0.014, 0.024) * cracks * strength
             * (0.22 + pressure * 0.15 + entry * 0.40 + condemned * 0.35);
    color = mix(color, vec3(max(color.r, 0.16), color.g * 0.72, color.b * 0.76),
                edge * strength * condemned * 0.13);

    // 低幅度颗粒随倒计时增强；中心噪声极弱，不叠加模糊或色差。
    float grain = grainHash(floor(texCoord * OutSize * 0.5) + floor(Time * 8.0)) - 0.5;
    color += grain * strength * (0.002 + pressure * 0.006) * (0.2 + edge * 0.8);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
