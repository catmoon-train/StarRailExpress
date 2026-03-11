package pro.fazeclan.river.stupid_express.client.mixin.modifier.split_personality;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * 隐藏旁观者的身体
 * 旁观者透明附身到主人格，但位置保持不变
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SplitPersonalityVisibilityMixin {

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    void hideSplitPersonalityObserverName(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractClientPlayer player) {
            var component = SplitPersonalityComponent.KEY.get(player);
            
            // 如果是旁观者，隐藏名牌
            if (component != null && component.getMainPersonality() != null && !component.isCurrentlyActive()) {
                cir.setReturnValue(false);
            }
        }
    }
}
