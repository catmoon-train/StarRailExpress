#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

float sampleGray(vec2 uv) {
    return luminance(texture(DiffuseSampler, uv).rgb);
}

// 采样一个很轻的局部均值，用来做“以留白为主”的自适应分层。
float localAverage(vec2 uv, vec2 texelSize) {
    float center = sampleGray(uv);
    float left = sampleGray(uv + vec2(-1.5, 0.0) * texelSize);
    float right = sampleGray(uv + vec2(1.5, 0.0) * texelSize);
    float up = sampleGray(uv + vec2(0.0, -1.5) * texelSize);
    float down = sampleGray(uv + vec2(0.0, 1.5) * texelSize);
    return (center * 2.0 + left + right + up + down) / 6.0;
}

float localContrast(float gray, float avg) {
    return abs(gray - avg);
}

// 边缘检测（Sobel）产生墨线感
float edgeDetect(vec2 uv, vec2 texelSize) {
    float tl = luminance(texture(DiffuseSampler, uv + vec2(-1, -1) * texelSize).rgb);
    float t  = luminance(texture(DiffuseSampler, uv + vec2( 0, -1) * texelSize).rgb);
    float tr = luminance(texture(DiffuseSampler, uv + vec2( 1, -1) * texelSize).rgb);
    float l  = luminance(texture(DiffuseSampler, uv + vec2(-1,  0) * texelSize).rgb);
    float r  = luminance(texture(DiffuseSampler, uv + vec2( 1,  0) * texelSize).rgb);
    float bl = luminance(texture(DiffuseSampler, uv + vec2(-1,  1) * texelSize).rgb);
    float b  = luminance(texture(DiffuseSampler, uv + vec2( 0,  1) * texelSize).rgb);
    float br = luminance(texture(DiffuseSampler, uv + vec2( 1,  1) * texelSize).rgb);

    float gx = -tl - 2.0*l - bl + tr + 2.0*r + br;
    float gy = -tl - 2.0*t - tr + bl + 2.0*b + br;
    return sqrt(gx*gx + gy*gy);
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    vec2 texelSize = 1.0 / OutSize;

    float gray = luminance(color.rgb);
    float avg = localAverage(texCoord, texelSize);
    float contrast = localContrast(gray, avg);

    // 提亮中间调，避免大量场景直接塌成黑块。
    float lifted = pow(clamp(gray, 0.0, 1.0), 0.72);
    float liftedAvg = pow(clamp(avg, 0.0, 1.0), 0.76);

    // 以留白为主：大部分区域优先保留白纸，只让很暗的块面少量落墨。
    float darkMass = 1.0 - smoothstep(0.13, 0.26, lifted + liftedAvg * 0.42);

    // 轮廓线仍然由边缘检测主导，但更强调中高对比轮廓，让人物更清楚。
    float edge = edgeDetect(texCoord, texelSize * 1.0);
    float edgeInk = smoothstep(0.11, 0.24, edge + contrast * 0.75);

    // 墨色来源：强轮廓 + 少量极暗区域；弱化块面压黑，避免人物整片糊掉。
    float inkMask = max(edgeInk, darkMass * 0.22);

    // 二值输出：默认白，只在 inkMask 覆盖处变成黑。
    float binary = 1.0 - step(0.46, inkMask);
    vec3 inkBw = vec3(binary);

    // 保留淡入淡出，但最终仍然只输出纯黑或纯白。
    vec3 finalColor = mix(color.rgb, inkBw, Strength);
    fragColor = vec4(finalColor, color.a);
}
