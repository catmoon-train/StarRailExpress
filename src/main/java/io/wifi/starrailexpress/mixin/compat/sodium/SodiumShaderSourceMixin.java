package io.wifi.starrailexpress.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 Sodium 加载地形顶点着色器源码时，注入景色偏移 UBO 的声明和应用。
 *
 * <p>
 * 注入后着色器效果：
 * </p>
 * 
 * <pre>
 *   layout(std140, binding = 2) uniform ubo_SectionOffsets {
 *       SRESceneryOffset SREOffsets[256];
 *   };
 *   // ... 在 _vert_init 末尾 ...
 *   _vert_position += SREOffsets[_draw_id].pos.xyz;
 * </pre>
 *
 * <p>
 * 对普通区块，UBO 中对应槽写入 (0,0,0)，偏移为零，不影响正常渲染。
 * </p>
 * <p>
 * 对景色区块，UBO 中写入 (areaOffset + scroll)，GPU 自动完成位移。
 * </p>
 */
@Mixin(value = ShaderLoader.class, remap = false)
public class SodiumShaderSourceMixin {

    private static final String SRE_UBO_DECL = "\nstruct SRESceneryOffset { vec4 pos; };\n"
            + "layout(std140) uniform ubo_SectionOffsets {\n"
            + "    SRESceneryOffset SREOffsets[256];\n"
            + "};\n";

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void sre$patchChunkVertexShader(ResourceLocation name,
            CallbackInfoReturnable<String> cir) {
        if (!name.getPath().contains("block_layer_opaque.vsh")) {
            return;
        }

        String src = cir.getReturnValue();

        // ── 步骤 1：在第一个 void 函数定义之前插入 UBO 声明 ──
        int firstVoidIdx = src.indexOf("\nvoid ");
        if (firstVoidIdx < 0) {
            return; // 着色器格式不符合预期，跳过，避免破坏正常渲染
        }
        src = src.substring(0, firstVoidIdx) + SRE_UBO_DECL + src.substring(firstVoidIdx);

        // ── 步骤 2：在 _vert_init 函数体末尾追加顶点偏移应用 ──
        // 与 SodiumTransformerMixin（wathe）采用相同的注入位置：函数末尾
        // 这样能保证在所有正常位置计算完成后才叠加偏移，不影响正常逻辑
        int vertInitIdx = src.indexOf("void _vert_init(");
        if (vertInitIdx >= 0) {
            int openBrace = src.indexOf('{', vertInitIdx);
            if (openBrace >= 0) {
                // 找匹配的右花括号
                int depth = 1;
                int i = openBrace + 1;
                while (i < src.length() && depth > 0) {
                    char c = src.charAt(i++);
                    if (c == '{')
                        depth++;
                    else if (c == '}')
                        depth--;
                }
                // i-1 是 '}'，在它之前插入偏移行
                int closeBrace = i - 1;
                src = src.substring(0, closeBrace)
                        + "    _vert_position += SREOffsets[_draw_id].pos.xyz;\n"
                        + src.substring(closeBrace);
            }
        }

        cir.setReturnValue(src);
    }
}