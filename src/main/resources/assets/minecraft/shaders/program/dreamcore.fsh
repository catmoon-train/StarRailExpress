#version 150

uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
uniform vec2 InSize;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 sizeBias = 1.0 / max(InSize + OutSize, vec2(1.0));
    vec2 wave = vec2(
        sin(texCoord.y * 40.0 + Time * 1.2 + sizeBias.x),
        cos(texCoord.x * 30.0 - Time + sizeBias.y)
    );
    vec2 uv = clamp(texCoord + wave * 0.002 * Strength, vec2(0.001), vec2(0.999));
    vec4 color = texture(DiffuseSampler, uv);
    color.r += 0.05 * Strength;
    color.b += 0.08 * Strength;
    fragColor = vec4(color.rgb, color.a);
}
