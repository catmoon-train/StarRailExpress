package net.exmo.mixin.client.side;

import dev.doctor4t.wathe.util.ShaderEditor;
import io.wifi.starrailexpress.compat.IrisHelper;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin {
    // _draw_id 是 section 在 region 内的本地索引（0..255），数组按它下标；
    // 须与 SodiumTransformerMixin 及 DefaultChunkRendererMixin 上传的缓冲大小一致
    private static final int SRE_OFFSET_COUNT = 256;

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private static void wathe$addVertexOffset(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        if (IrisHelper.isIrisShaderPackInUse()) {
            return;
        }

        if (name.getPath().contains("block_layer") && name.getPath().endsWith(".vsh")) {
            String modifiedShader = new ShaderEditor(cir.getReturnValue())
                    .addUniform("struct Offset { vec4 pos; };")
                    .addUniform("layout(std140) uniform ubo_SectionOffsets { Offset Offsets["
                            + SRE_OFFSET_COUNT + "]; };")
                    .addBefore("_vert_position",
                            "    _vert_position += Offsets[_draw_id].pos.xyz;")
                    .build();

            cir.setReturnValue(modifiedShader);
        }
    }
}
