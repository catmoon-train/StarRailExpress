#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float SettlementTime;
uniform float SettlementBlur;
in vec2 texCoord;
out vec4 fragColor;

float grain(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233)) + floor(SettlementTime * 24.0)) * 43758.5453);
}

vec3 blurred(vec2 uv) {
    vec2 stepSize = vec2(SettlementBlur) / InSize;
    vec3 c = texture(DiffuseSampler, clamp(uv, 0.001, 0.999)).rgb * 0.4;
    c += texture(DiffuseSampler, clamp(uv + vec2(stepSize.x, 0.0), 0.001, 0.999)).rgb * 0.15;
    c += texture(DiffuseSampler, clamp(uv - vec2(stepSize.x, 0.0), 0.001, 0.999)).rgb * 0.15;
    c += texture(DiffuseSampler, clamp(uv + vec2(0.0, stepSize.y), 0.001, 0.999)).rgb * 0.15;
    c += texture(DiffuseSampler, clamp(uv - vec2(0.0, stepSize.y), 0.001, 0.999)).rgb * 0.15;
    return c;
}

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    vec2 shift = centered * dot(centered, centered) * 0.65 / InSize;
    vec3 color = blurred(texCoord);
    color.r = blurred(texCoord + shift).r;
    color.b = blurred(texCoord - shift).b;
    color *= vec3(1.065, 1.012, 0.925);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 0.92);
    color *= 1.0 - smoothstep(0.28, 1.55, dot(centered, centered)) * 0.38;
    color += (grain(gl_FragCoord.xy) - 0.5) * 0.026;
    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
