package io.wifi.starrailexpress.mixin.compat.sodium;

import io.wifi.starrailexpress.compat.sodium.SRESceneryShaderInterface;
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
 * 向 Sodium 的 DefaultShaderInterface 注入 UBO 绑定点。
 * 绑定点 2（Sodium 自用 0，wathe 用 1，SRE 用 2，避免冲突）。
 */
@Mixin(value = DefaultShaderInterface.class, remap = false)
public class SodiumDefaultShaderInterfaceMixin implements SRESceneryShaderInterface {

    @Unique
    private GlUniformBlock sre$sceneryUniform;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sre$bindUniformBlock(ShaderBindingContext context,
            ChunkShaderOptions options,
            CallbackInfo ci) {
        // bindUniformBlock 会安静地返回 null 如果着色器中没有该 UBO，不会报错
//        sre$sceneryUniform = context.bindUniformBlock("ubo_SectionOffsets", 2);
    }

    @Override
    public void sre$setSceneryOffsets(GlMutableBuffer buffer) {
//        if (sre$sceneryUniform != null) {
//            sre$sceneryUniform.bindBuffer(buffer);
//        }
    }
}