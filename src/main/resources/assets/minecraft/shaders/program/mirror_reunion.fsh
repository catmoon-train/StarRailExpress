#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    vec2 center = uv - vec2(0.5);
    float dist = length(center);
    vec2 dir = normalize(center + vec2(0.0001));

    vec2 grid = floor(uv * vec2(22.0, 14.0));
    float crack = step(0.88, hash(grid));
    vec2 shard = (vec2(hash(grid + 1.7), hash(grid + 4.1)) - 0.5) * 0.014 * Strength * crack;

    float wave = sin(dist * 28.0 - Time * 4.2) * 0.0022 * Strength;
    vec2 duv = uv + shard + dir * wave;

    float ab = 0.0055 * Strength * (0.7 + 0.3 * sin(Time * 2.4));
    float r = texture(DiffuseSampler, duv + dir * ab).r;
    float g = texture(DiffuseSampler, duv).g;
    float b = texture(DiffuseSampler, duv - dir * ab).b;
    vec3 col = vec3(r, g, b);

    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 silver = vec3(gray * 0.86, gray * 0.93, gray * 1.08);
    col = mix(col, silver, 0.58 * Strength);

    float vig = smoothstep(1.08, 0.22, dist);
    col *= mix(1.0, vig, Strength * 0.75);

    vec4 base = texture(DiffuseSampler, uv);
    fragColor = vec4(mix(base.rgb, col, clamp(Strength, 0.0, 1.0)), base.a);
}
