#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 ProjectionInv;
uniform vec2 OutSize;
uniform float Strength;
uniform float FocusDistance;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstructView(vec2 uv, float depth) {
    vec4 clipPosition = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPosition = ProjectionInv * clipPosition;
    return viewPosition.xyz / max(abs(viewPosition.w), 0.00001);
}

float sceneDepth(vec2 uv) {
    float depth = texture(DepthSampler, clamp(uv, vec2(0.0), vec2(1.0))).r;
    if (depth <= 0.002 || depth >= 0.9992) {
        return 80.0;
    }
    return length(reconstructView(uv, depth));
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float dist = sceneDepth(texCoord);
    float focus = max(FocusDistance, 0.75);
    float blur = clamp((dist - focus) / max(focus * 2.4, 1.0), 0.0, 1.0) * Strength;
    if (blur <= 0.002) {
        fragColor = color;
        return;
    }

    vec2 pixel = (1.0 / max(OutSize, vec2(1.0))) * blur * 7.5;
    vec4 sum = color * 0.18;
    sum += texture(DiffuseSampler, texCoord + vec2(pixel.x, 0.0)) * 0.14;
    sum += texture(DiffuseSampler, texCoord - vec2(pixel.x, 0.0)) * 0.14;
    sum += texture(DiffuseSampler, texCoord + vec2(0.0, pixel.y)) * 0.14;
    sum += texture(DiffuseSampler, texCoord - vec2(0.0, pixel.y)) * 0.14;
    sum += texture(DiffuseSampler, texCoord + pixel) * 0.09;
    sum += texture(DiffuseSampler, texCoord - pixel) * 0.09;
    sum += texture(DiffuseSampler, texCoord + vec2(pixel.x, -pixel.y)) * 0.08;
    sum += texture(DiffuseSampler, texCoord + vec2(-pixel.x, pixel.y)) * 0.08;
    fragColor = sum;
}
