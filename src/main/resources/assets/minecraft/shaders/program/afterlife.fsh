#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D NoiseSampler;
uniform sampler2D DirectionSampler;
uniform sampler2D SuperNoiseSampler;
uniform sampler2D DitherSampler;
uniform sampler2D ContrastSampler;

uniform float Strength;
uniform float Time;
uniform float EffectTime;
uniform float Darkness;
uniform vec2 InSize;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 sampleScene(vec2 uv, float chroma) {
    uv = clamp(uv, vec2(0.001), vec2(0.999));

    vec3 color;
    color.r = texture(DiffuseSampler, clamp(uv + vec2(chroma, 0.0), vec2(0.001), vec2(0.999))).r;
    color.g = texture(DiffuseSampler, uv + vec2(0.0, chroma * 0.18)).g;
    color.b = texture(DiffuseSampler, clamp(uv - vec2(chroma * 0.75, 0.0), vec2(0.001), vec2(0.999))).b;
    return color;
}

void main() {
    float strength = clamp(Strength, 0.0, 1.0);
    float t = EffectTime + Time * 0.02;
    vec2 screen = max(OutSize + InSize * 0.000001, vec2(1.0));
    float aspect = screen.x / screen.y;

    vec2 uv = texCoord;
    vec2 center = vec2(0.5);
    vec2 lensVec = (uv - center) * vec2(aspect, 1.0);
    float lens = dot(lensVec, lensVec);
    float dist = length(lensVec);

    float breath = sin(t * 1.35) * 0.5 + 0.5;
    float blink = smoothstep(0.92, 1.0, sin(t * 0.43) * 0.5 + 0.5);
    vec2 sway = vec2(
        sin(t * 0.83) * 0.006 + sin(t * 2.10) * 0.0016,
        cos(t * 0.71) * 0.004 + sin(t * 1.31) * 0.0012
    ) * strength;

    vec2 roomUv = vec2(uv.x * aspect, uv.y);
    vec2 dir = texture(DirectionSampler, fract(roomUv * 0.34 + vec2(t * 0.017, -t * 0.011))).rg * 2.0 - 1.0;
    float wallFog = texture(SuperNoiseSampler, fract(roomUv * vec2(0.75, 0.44) + vec2(t * 0.018, -t * 0.012))).r;
    float crawl = texture(NoiseSampler, fract(roomUv * vec2(3.4, 2.2) + dir * 0.12 + vec2(-t * 0.065, t * 0.032))).r;
    float stain = texture(ContrastSampler, fract(roomUv * vec2(1.08, 0.74) + dir * 0.045 + vec2(t * 0.006, t * 0.014))).r;

    vec2 povVec = uv - center;
    float barrel = lens * (0.23 + breath * 0.035) * strength;
    vec2 sampleUv = center + povVec * (1.0 + barrel);
    sampleUv += sway;
    sampleUv += dir * (0.006 + crawl * 0.008) * strength * smoothstep(0.04, 0.88, dist);

    float chroma = (0.0015 + lens * 0.0048 + crawl * 0.0022) * strength;
    vec3 color = sampleScene(sampleUv, chroma);
    float alpha = texture(DiffuseSampler, texCoord).a;

    float edgeBlur = smoothstep(0.26, 0.82, dist) * strength;
    vec2 blurDir = normalize(povVec + dir * 0.02 + vec2(0.0001));
    vec2 blurStep = blurDir * (0.002 + lens * 0.010) * edgeBlur;
    vec3 blurColor = color;
    blurColor += sampleScene(sampleUv + blurStep, chroma * 0.5);
    blurColor += sampleScene(sampleUv - blurStep, chroma * 0.5);
    blurColor += sampleScene(sampleUv + blurStep * 2.0, chroma * 0.35);
    blurColor *= 0.25;
    color = mix(color, blurColor, edgeBlur * 0.72);

    float gray = luma(color);
    vec3 fluorescentYellow = vec3(1.16, 1.03, 0.58);
    vec3 wetShadow = vec3(0.22, 0.21, 0.10);
    vec3 oldCarpet = vec3(0.48, 0.40, 0.16);
    vec3 backrooms = mix(vec3(gray), color * fluorescentYellow, 0.36);

    float wallpaper = sin((uv.x + dir.x * 0.017 + wallFog * 0.034) * 168.0) * 0.5 + 0.5;
    wallpaper = smoothstep(0.56, 0.78, wallpaper);
    float panel = sin((uv.x + dir.y * 0.009) * 23.0) * 0.5 + 0.5;
    panel = smoothstep(0.70, 0.93, panel);
    backrooms = mix(backrooms, backrooms * fluorescentYellow, wallpaper * 0.12 * strength);
    backrooms = mix(backrooms, oldCarpet, panel * 0.08 * strength);

    float fluorescent = 0.73 + 0.24 * sin(uv.y * 38.0 + t * 7.4);
    fluorescent += (hash21(vec2(floor(t * 20.0), floor(uv.y * 108.0))) - 0.5) * 0.18;
    fluorescent -= blink * 0.34;
    fluorescent = clamp(fluorescent, 0.38, 1.08);

    float dampMask = smoothstep(0.42, 0.88, stain) * (0.30 + smoothstep(0.16, 0.84, dist) * 0.58);
    dampMask += smoothstep(0.64, 0.94, wallFog) * 0.25;
    backrooms = mix(backrooms, wetShadow, clamp(dampMask, 0.0, 1.0) * 0.42 * strength);

    float ceiling = smoothstep(0.76, 1.0, uv.y) * (0.22 + wallFog * 0.22);
    float floorBand = (1.0 - smoothstep(0.0, 0.20, uv.y)) * (0.22 + stain * 0.16);
    float ovalVignette = 1.0 - smoothstep(0.38, 0.98, dist);
    float eyePressure = 1.0 - (1.0 - ovalVignette) * (0.58 + breath * 0.10) * strength;

    backrooms *= fluorescent;
    backrooms *= eyePressure;
    backrooms = mix(backrooms, wetShadow, (ceiling + floorBand) * strength);

    float staticNoise = texture(NoiseSampler, fract(uv * screen / 256.0 + vec2(t * 0.91, -t * 0.37))).r;
    float dither = texture(DitherSampler, fract(uv * screen / 256.0)).r;

    backrooms += (staticNoise - 0.5) * 0.070 * strength;
    backrooms += (dither - 0.5) * 0.030 * strength;

    float outOfBounds = smoothstep(0.001, 0.045, min(min(sampleUv.x, sampleUv.y), min(1.0 - sampleUv.x, 1.0 - sampleUv.y)));
    vec3 finalColor = mix(color, backrooms, strength);
    finalColor = mix(wetShadow * 0.55, finalColor, outOfBounds);
    finalColor = clamp(finalColor, 0.0, 1.0);
    finalColor *= 1.0 - clamp(Darkness, 0.0, 0.5);

    fragColor = vec4(finalColor, alpha);
}
