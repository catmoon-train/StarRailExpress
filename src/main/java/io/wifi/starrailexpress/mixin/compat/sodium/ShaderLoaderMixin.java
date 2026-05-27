package io.wifi.starrailexpress.mixin.compat.sodium;

import io.wifi.starrailexpress.util.ShaderEditor;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium Shader Loader Mixin
 * 
 * 修改 Sodium Chunk Shader，添加 Uniform Buffer Block 支持
 * 用于传递区块偏移数据到 GPU
 */
@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void sre$addVertexOffset(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        String shaderPath = name.getPath();
        
        // 只修改不透明的块层着色器（主要渲染层）
        if (shaderPath.contains("block_layer_opaque.vsh")) {
            String originalShader = cir.getReturnValue();
            if (originalShader == null) {
                return;
            }
            
            String modifiedShader = new ShaderEditor(originalShader)
                    .addUniform("struct Offset { vec4 pos; };")
                    .addUniform("layout(std140) uniform ubo_SectionOffsets { Offset Offsets[256]; };")
                    .addBefore("_vert_position",
                            "    _vert_position += Offsets[_draw_id].pos.xyz;")
                    .build();

            cir.setReturnValue(modifiedShader);
        }
    }
}
