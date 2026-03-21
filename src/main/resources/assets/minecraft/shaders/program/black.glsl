#version 150

uniform sampler2D DiffuseSampler;
uniform float BlackScreenStrength; // 0.0为完全透明(正常画面)，1.0为完全黑色
uniform vec3 ColorTint; // 可选的颜色色调

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // 获取原始画面颜色
    vec4 originalColor = texture(DiffuseSampler, texCoord);
    
    // 创建黑色层（可添加轻微色调变化）
    vec3 blackColor = vec3(0.0, 0.0, 0.0);
    
    // 根据强度混合原始颜色和黑色
    vec3 finalColor = mix(originalColor.rgb, blackColor, BlackScreenStrength);
    
    // 应用可选的颜色色调（模拟不同氛围）
    if (BlackScreenStrength > 0.8) {
        finalColor = mix(finalColor, ColorTint * 0.3, (BlackScreenStrength - 0.8) * 5.0);
    }
    
    fragColor = vec4(finalColor, originalColor.a);
}
