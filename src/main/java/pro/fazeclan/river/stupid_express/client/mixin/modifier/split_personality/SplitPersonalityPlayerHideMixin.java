package pro.fazeclan.river.stupid_express.client.mixin.modifier.split_personality;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

/**
 * 隐藏旁观者的完整渲染
 * 旁观者对其他玩家来说是不可见的，但保持位置同步
 */
@Mixin(PlayerRenderer.class)
public abstract class SplitPersonalityPlayerHideMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    void preventSplitPersonalityObserverRender(AbstractClientPlayer player, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        var component = SplitPersonalityComponent.KEY.get(player);
        
        // 如果是旁观者（非活跃人格），完全隐藏其渲染
        if (component != null && component.getMainPersonality() != null && !component.isCurrentlyActive()) {
            AbstractClientPlayer mainPlayer = (AbstractClientPlayer) player.level().getPlayerByUUID(component.getMainPersonality());
            
            // 只在主人格存在时隐藏旁观者
            if (mainPlayer != null && mainPlayer != player) {
                ci.cancel();
                return;
            }
        }
    }
}
