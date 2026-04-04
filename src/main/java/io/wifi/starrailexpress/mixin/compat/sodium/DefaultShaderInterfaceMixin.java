package io.wifi.starrailexpress.mixin.compat.sodium;


import io.wifi.starrailexpress.compat.SodiumShaderInterface;
// import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
// import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DefaultShaderInterface.class)
public abstract class DefaultShaderInterfaceMixin implements SodiumShaderInterface {
    // @Unique
    // private GlUniformBlock uniformOffsets;

    // @Inject(method = "<init>", at = @At("RETURN"))
    // private void tmm$addUniform(ShaderBindingContext context, ChunkShaderOptions options,
    //                             CallbackInfo ci) {
    //     if (IrisHelper.isIrisShaderPackInUse()) {
    //         return;
    //     }

    //     uniformOffsets = context.bindUniformBlock("ubo_SectionOffsets", 1);
    // }

    // @Override
    // public void tmm$set(GlMutableBuffer buffer) {
    //     if (uniformOffsets == null) {
    //         return;
    //     }

    //     uniformOffsets.bindBuffer(buffer);
    // }
}
