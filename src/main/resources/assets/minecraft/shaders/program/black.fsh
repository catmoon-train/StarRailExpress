#version 150

uniform sampler2D DiffuseSampler;
uniform float BlackStrength; // 黑屏强度 (0.0-1.0)
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // 获取原始颜色
    vec4 color = texture(DiffuseSampler, texCoord);
    
    // 创建黑色层
    vec3 blackLayer = vec3(0.0, 0.0, 0.0);
    
    // 根据强度混合原始颜色和黑色
    // 使用平滑过渡
    float transition = smoothstep(0.0, 1.0, BlackStrength);
    vec3 finalColor = mix(color.rgb, blackLayer, transition);
    
    fragColor = vec4(finalColor, color.a);
}