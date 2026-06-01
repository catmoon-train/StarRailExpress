#version 150

uniform sampler2D DiffuseSampler;
uniform float Strength;
uniform float Time;
uniform vec2 InSize;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    vec2 sizeBias = 1.0 / max(InSize + OutSize, vec2(1.0));
    float glow = 0.5 + 0.5 * sin(Time * 1.6 + texCoord.y * 18.0 + sizeBias.x);
    color.rgb += vec3(0.18, 0.28, 0.32) * glow * Strength;
    fragColor = vec4(color.rgb, color.a);
}
