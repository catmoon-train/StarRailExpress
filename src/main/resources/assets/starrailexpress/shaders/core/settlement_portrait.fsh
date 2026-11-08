#version 150
uniform sampler2D Sampler0;
uniform vec2 TexelSize;
uniform float OutlineWidth;
uniform float Opacity;
uniform float Shadow;
uniform vec4 ColorModulator;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
float alphaAt(vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    return texture(Sampler0, uv).a;
}
void main() {
    vec4 base = texture(Sampler0, texCoord);
    float edge = base.a;
    for (int i = 0; i < 12; ++i) {
        float a = float(i) * 6.2831853 / 12.0;
        vec2 stepUV = vec2(cos(a), sin(a)) * TexelSize * OutlineWidth;
        edge = max(edge, alphaAt(texCoord + stepUV));
        edge = max(edge, alphaAt(texCoord + stepUV * 0.5));
    }
    float alpha = edge * Opacity * vertexColor.a * ColorModulator.a;
    if (alpha < 0.003) discard;
    // 独立透明目标的 RGB 已经预乘过透明度，合成前还原，避免皮肤边缘出现黑边。
    vec3 skin = base.rgb / max(base.a, 0.001);
    vec3 color = mix(vec3(0.98, 0.956, 0.89), skin, base.a);
    color = mix(color, vec3(0.055, 0.09, 0.12), Shadow);
    fragColor = vec4(color * vertexColor.rgb * ColorModulator.rgb, alpha);
}
