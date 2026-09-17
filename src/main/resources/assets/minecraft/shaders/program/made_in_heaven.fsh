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

    float pulse = 0.5 + 0.5 * sin(Time * 7.0);
    float warp = Strength * (0.035 + 0.03 * pulse);
    vec2 streak = dir * warp * (0.35 + dist);
    vec2 spun = uv;
    float ang = Strength * 0.35 * sin(Time * 1.7 + dist * 8.0);
    float ca = cos(ang);
    float sa = sin(ang);
    spun = vec2(
        toCenter.x * ca - toCenter.y * sa,
        toCenter.x * sa + toCenter.y * ca
    ) + center + streak;

    float ab = 0.012 * Strength * (0.7 + 0.3 * sin(Time * 5.0));
    float r = texture(DiffuseSampler, spun + dir * ab).r;
    float g = texture(DiffuseSampler, spun).g;
    float b = texture(DiffuseSampler, spun - dir * ab).b;
    vec3 col = vec3(r, g, b);

    float hue = fract(Time * 0.08 + dist * 0.35);
    vec3 gold = vec3(1.0, 0.86, 0.45);
    vec3 violet = vec3(0.72, 0.42, 1.0);
    vec3 tint = mix(gold, violet, hue);
    col = mix(col, col * tint * 1.25, Strength * 0.55);

    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(gray), col, 1.0 + Strength * 0.35);

    float rays = pow(max(0.0, 1.0 - dist), 3.0) * Strength * (0.25 + 0.2 * pulse);
    col += gold * rays;

    float vignette = smoothstep(1.15, 0.25, dist);
    col *= mix(1.0, vignette, Strength * 0.45);

    vec4 base = texture(DiffuseSampler, uv);
    vec3 finalColor = mix(base.rgb, col, clamp(Strength, 0.0, 1.0));
    fragColor = vec4(finalColor, base.a);
}
