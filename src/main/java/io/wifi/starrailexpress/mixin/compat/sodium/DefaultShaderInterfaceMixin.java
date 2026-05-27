package io.wifi.starrailexpress.mixin.compat.sodium;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.compat.SodiumShaderInterface;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformBlock;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium Shader Interface Mixin 
 * 
 * 为 Sodium Chunk Shader 添加 Uniform Buffer Block 支持
 * 用于传递区块偏移数据到 GPU Shader
 */
@Mixin(DefaultShaderInterface.class)
public abstract class DefaultShaderInterfaceMixin implements SodiumShaderInterface {
    @Unique
    private GlUniformBlock uniformOffsets;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void tmm$addUniform(ShaderBindingContext context, ChunkShaderOptions options,
                                CallbackInfo ci) {
        // 绑定 Uniform Buffer Block 用于传递区块偏移数据
        // binding point 1 用于 ubo_SectionOffsets
        try {
            uniformOffsets = context.bindUniformBlock("ubo_SectionOffsets", 1);
        } catch (Exception e) {
            uniformOffsets = null;
        }
    }

    @Override
    public void tmm$set(GlMutableBuffer buffer) {
        if (uniformOffsets == null) {
            return;
        }

        // 将缓冲区绑定到 Uniform Block
        uniformOffsets.bindBuffer(buffer);
    }
}
