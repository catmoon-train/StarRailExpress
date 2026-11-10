package pro.fazeclan.river.stupid_express.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.client.ArsonistIntroClient;

/** 打火机近景不渲染纵火犯本体，避免模型或大尺寸皮肤挡住落点。 */
@Mixin(PlayerRenderer.class)
public abstract class ArsonistIntroPlayerRenderMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void stupidExpress$clearLighterView(AbstractClientPlayer player, float yaw, float partialTick,
            PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (ArsonistIntroClient.hideOwner(player)) ci.cancel();
    }
}
