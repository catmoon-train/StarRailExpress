#version 150

uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
uniform float RedPulse;
uniform float Darkness;
uniform float Vignette;
uniform float Madness;

in vec2 texCoord;
out vec4 fragColor;

float random(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 center = vec2(0.5, 0.5);
    vec2 fromCenter = texCoord - center;
    float wobble = sin((texCoord.y * 34.0 + Time * 7.0)) * 0.0035 * Madness * Strength;
    vec2 uv = texCoord + vec2(wobble, sin(texCoord.x * 21.0 + Time * 5.0) * 0.0025 * Madness * Strength);

    float chroma = 0.004 * Strength * (0.3 + Madness);
    vec3 color;
    color.r = texture(DiffuseSampler, uv + vec2(chroma, 0.0)).r;
    color.g = texture(DiffuseSampler, uv).g;
    color.b = texture(DiffuseSampler, uv - vec2(chroma, 0.0)).b;

    float dist = length(fromCenter * vec2(1.0, 0.78));
    float vignette = smoothstep(0.28, 0.88, dist) * Vignette;
    color = mix(color, vec3(0.0), clamp(vignette + Darkness, 0.0, 0.95));

    float bloodVein = smoothstep(0.66, 0.95, dist + random(floor(texCoord * 22.0) + floor(Time * 8.0)) * 0.12);
    vec3 blood = vec3(0.58 + 0.2 * sin(Time * 4.0), 0.0, 0.015);
    color = mix(color, blood, clamp((RedPulse * 0.42 + Strength * 0.16) * bloodVein, 0.0, 0.75));

    float grain = (random(texCoord * vec2(480.0, 270.0) + Time) - 0.5) * 0.12 * Strength;
    color += grain;
    color = mix(vec3(dot(color, vec3(0.299, 0.587, 0.114))), color, 1.0 + Madness * 0.35);

    fragColor = vec4(clamp(color, 0.0, 1.0), texture(DiffuseSampler, texCoord).a);
}
