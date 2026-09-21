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

    float s = clamp(Strength, 0.0, 1.0);
    // 低强度几乎只有暖金染色；旋绕/挤压随强度平方爬升，避免转职与咏诵前眩晕
    float tintAmt = s * 0.38;
    float warp = pow(s, 2.35) * 0.48;

    float crush = mix(1.0, 0.78, warp);
    float ang = warp * (0.08 + Time * 0.16) + dist * warp * 0.28;
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 crushed = toCenter * crush;
    vec2 spun = vec2(
        crushed.x * ca - crushed.y * sa,
        crushed.x * sa + crushed.y * ca
    );
    vec2 warped = center + spun + dir * (warp * 0.12 * dist * dist);

    float ab = 0.012 * warp;
    float r = texture(DiffuseSampler, warped + dir * ab).r;
    float g = texture(DiffuseSampler, warped).g;
    float b = texture(DiffuseSampler, warped - dir * ab).b;
    vec3 col = vec3(r, g, b);

    vec3 gold = vec3(1.0, 0.91, 0.66);
    vec3 warm = vec3(1.0, 0.96, 0.88);
    vec3 tint = mix(warm, gold, clamp(dist * 0.75, 0.0, 1.0));
    col = mix(col, col * tint, tintAmt);

    float glow = pow(max(0.0, 1.0 - dist), 3.0) * tintAmt * 0.16;
    col += gold * glow;

    float vignette = smoothstep(1.4, 0.28, dist);
    col *= mix(1.0, vignette, tintAmt * 0.28);

    vec4 base = texture(DiffuseSampler, uv);
    float mixAmt = clamp(tintAmt * 1.05 + warp * 0.35, 0.0, 0.85);
    vec3 finalColor = mix(base.rgb, col, mixAmt);
    fragColor = vec4(finalColor, base.a);
}
