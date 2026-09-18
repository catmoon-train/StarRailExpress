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

    // 只随 Strength 单向加深，Time 只做缓慢连转，不再用 sin 来回抽
    float s = clamp(Strength, 0.0, 1.0);
    float s2 = s * s;
    float crush = mix(1.0, 0.08, s2);
    float ang = s * (0.12 + Time * 0.22) + dist * s * 0.35;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 crushed = toCenter * crush;
    vec2 spun = vec2(
        crushed.x * ca - crushed.y * sa,
        crushed.x * sa + crushed.y * ca
    );
    vec2 warped = center + spun + dir * (s2 * 0.28 * dist);

    float ab = 0.03 * s2;
    float r = texture(DiffuseSampler, warped + dir * ab).r;
    float g = texture(DiffuseSampler, warped).g;
    float b = texture(DiffuseSampler, warped - dir * ab).b;
    vec3 col = vec3(r, g, b);

    vec3 gold = vec3(1.0, 0.86, 0.42);
    vec3 violet = vec3(0.76, 0.36, 1.0);
    vec3 tint = mix(gold, violet, clamp(dist * 1.1, 0.0, 1.0));
    col = mix(col, col * tint * 1.35, s * 0.65);

    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(gray), col, 1.0 + s * 0.4);

    float rays = pow(max(0.0, 1.0 - dist), 2.6) * s2 * 0.55;
    col += gold * rays;

    float vignette = smoothstep(1.2, 0.16, dist);
    col *= mix(1.0, vignette, s * 0.5);

    vec4 base = texture(DiffuseSampler, uv);
    vec3 finalColor = mix(base.rgb, col, s);
    fragColor = vec4(finalColor, base.a);
}
