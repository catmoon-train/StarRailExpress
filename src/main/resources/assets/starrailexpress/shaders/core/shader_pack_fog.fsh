#version 150

uniform sampler2D DepthSampler;
uniform mat4 InverseProjection;
uniform mat4 InverseModelView;
uniform vec2 FogRange;
uniform int SreFogShape;
uniform vec3 SreFogColor;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    // 天空也必须完全遮住，否则远方天空/光柱仍会绕过视野限制。
    float amount = 1.0;
    if (depth < 1.0) {
        vec4 view = InverseProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec3 position = view.xyz / view.w;
        // 圆柱雾遵循原版 max(水平半径, 竖直距离)，不能直接用屏幕空间 z。
        vec3 world = mat3(InverseModelView) * position;
        float distance = SreFogShape == 0 ? length(position) : max(length(world.xz), abs(world.y));
        float linearFog = clamp((distance - FogRange.x) / max(FogRange.y - FogRange.x, 0.0001), 0.0, 1.0);
        amount = linearFog * linearFog * (3.0 - 2.0 * linearFog);
    }
    fragColor = vec4(SreFogColor, amount);
}
