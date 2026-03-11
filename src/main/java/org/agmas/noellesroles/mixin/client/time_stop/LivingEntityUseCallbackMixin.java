package org.agmas.noellesroles.mixin.client.time_stop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityUseCallbackMixin {
    @Inject(method = "getUseItemRemainingTicks", at = @At("HEAD"), cancellable = true)
    public void getUseItemRemainingTicks(CallbackInfoReturnable<Integer> cir) {
        if (!(((LivingEntity) (Object) this) instanceof Player))
            return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player!=null){
            if (player.hasEffect(ModEffects.TIME_STOP)){
                if (!TimeStopEffect.canMovePlayers.contains(player.getUUID())){
                    cir.setReturnValue(0);
                }
            }
        }
    }
}
