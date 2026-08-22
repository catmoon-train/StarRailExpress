#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength;
uniform float Time;
uniform float Progress;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(0.5, 0.5);
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 radial = uv - center;
    radial.x *= aspect;
    float dist = length(radial);
    vec2 dir = radial / max(dist, 0.0001);

    // The scene winds backwards into a cyan/violet temporal vortex.
    float direction = mix(-1.0, 1.0, smoothstep(0.46, 0.54, Progress));
    float spiralBands = sin(dist * 72.0 - Time * 11.0 - Progress * 28.0);
    float ang = direction * Strength * (1.0 - smoothstep(0.05, 0.82, dist))
            * (0.34 + spiralBands * 0.055);
    float ca = cos(ang);
    float sa = sin(ang);
    vec2 spun = vec2(radial.x * ca - radial.y * sa,
                     radial.x * sa + radial.y * ca);
    spun.x /= aspect;
    vec2 warped = center + spun;

    float shock = sin(dist * 92.0 - Progress * 42.0 + Time * 4.0);
    float ring = exp(-95.0 * abs(dist - (0.08 + Progress * 0.72)));
    vec2 waveOffset = (dir / vec2(aspect, 1.0))
            * Strength * (shock * 0.0027 + ring * 0.012);
    warped = clamp(warped + waveOffset, vec2(0.001), vec2(0.999));

    // Four backwards samples make a short temporal echo instead of a flat blur.
    vec2 echoStep = (dir / vec2(aspect, 1.0)) * Strength * 0.0035;
    vec3 echo = texture(DiffuseSampler, warped).rgb * 0.52;
    echo += texture(DiffuseSampler, clamp(warped + echoStep, 0.0, 1.0)).rgb * 0.24;
    echo += texture(DiffuseSampler, clamp(warped + echoStep * 2.2, 0.0, 1.0)).rgb * 0.15;
    echo += texture(DiffuseSampler, clamp(warped + echoStep * 4.0, 0.0, 1.0)).rgb * 0.09;

    float aberration = Strength * (0.0035 + ring * 0.010 + abs(spiralBands) * 0.0015);
    float r = texture(DiffuseSampler, clamp(warped + dir / vec2(aspect, 1.0) * aberration, 0.0, 1.0)).r;
    float g = echo.g;
    float b = texture(DiffuseSampler, clamp(warped - dir / vec2(aspect, 1.0) * aberration, 0.0, 1.0)).b;
    vec3 col = mix(echo, vec3(r, g, b), 0.72);

    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 cyan = vec3(0.10, 0.88, 1.15);
    vec3 violet = vec3(0.72, 0.18, 1.08);
    vec3 temporalTint = mix(cyan, violet, 0.5 + 0.5 * sin(Time * 2.4 + dist * 18.0));
    vec3 dazed = mix(col, vec3(gray) * 0.45 + temporalTint * 0.68, Strength * 0.52);

    float scanline = 0.94 + 0.06 * sin(uv.y * OutSize.y * 1.35 - Time * 18.0);
    dazed *= mix(1.0, scanline, Strength);
    dazed += temporalTint * (ring * 0.48 + max(spiralBands, 0.0) * 0.025) * Strength;

    float vignette = 1.0 - smoothstep(0.32, 0.90, dist) * 0.72 * Strength;
    dazed *= vignette;

    // A controlled arrival flash hides the exact authoritative NBT restore tick.
    float arrival = smoothstep(0.82, 0.96, Progress) * (1.0 - smoothstep(0.985, 1.0, Progress));
    dazed = mix(dazed, vec3(0.78, 0.96, 1.0), arrival * 0.72);

    vec4 base = texture(DiffuseSampler, uv);
    vec3 finalColor = mix(base.rgb, dazed, clamp(Strength, 0.0, 1.0));
    fragColor = vec4(finalColor, base.a);
}
