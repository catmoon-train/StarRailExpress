package org.agmas.noellesroles.mixin.client.command_block;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.MustardPuppetAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将玩家收到的铁傀儡攻击事件交给外观动画。 */
@Mixin(LivingEntity.class)
public class MustardPuppetEventMixin {
    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void mustard$attackEvent(byte event, CallbackInfo ci) {
        if (event == 4 && (Object) this instanceof Player player && player.level().isClientSide) {
            MustardPuppetAnimation.onAttack(player);
        }
    }
}
