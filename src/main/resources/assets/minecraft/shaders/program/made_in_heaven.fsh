#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(0.5, 0.5);
    vec2 toCenter = uv - center;
    float dist = length(toCenter);
    vec2 dir = normalize(toCenter + vec2(0.0001));

    // 只随 Strength 单向加深；Time 只做单向连转，不用 sin 来回抽
    float s = clamp(Strength, 0.0, 1.0);
    float warp = clamp(s * 1.15, 0.0, 1.0);
    float crush = mix(1.0, 0.03, warp);
    float ang = warp * (0.75 + Time * 1.05) + dist * warp * 2.4;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 crushed = toCenter * crush;
    vec2 spun = vec2(
        crushed.x * ca - crushed.y * sa,
        crushed.x * sa + crushed.y * ca
    );
    vec2 warped = center + spun + dir * (warp * 0.85 * dist * dist);

    float ab = 0.12 * warp;
    float r = texture(DiffuseSampler, warped + dir * ab).r;
    float g = texture(DiffuseSampler, warped).g;
    float b = texture(DiffuseSampler, warped - dir * ab).b;
    vec3 col = vec3(r, g, b);

    vec3 gold = vec3(1.0, 0.86, 0.42);
    vec3 violet = vec3(0.76, 0.36, 1.0);
    vec3 tint = mix(gold, violet, clamp(dist * 1.25, 0.0, 1.0));
    col = mix(col, col * tint * 1.55, warp * 0.8);

    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(gray), col, 1.0 + warp * 0.55);

    float rays = pow(max(0.0, 1.0 - dist), 2.2) * warp * 0.7;
    col += gold * rays;

    float vignette = smoothstep(1.25, 0.08, dist);
    col *= mix(1.0, vignette, warp * 0.72);

    vec4 base = texture(DiffuseSampler, uv);
    vec3 finalColor = mix(base.rgb, col, warp);
    fragColor = vec4(finalColor, base.a);
}
