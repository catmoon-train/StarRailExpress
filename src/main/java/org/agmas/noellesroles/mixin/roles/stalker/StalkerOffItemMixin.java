package org.agmas.noellesroles.mixin.roles.stalker;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class StalkerOffItemMixin {

    @Inject(method = "getMainArm", at = @At("RETURN"), cancellable = true)
    private void mainHand(CallbackInfoReturnable<HumanoidArm> cir) {
        final Player player = (Player) (Object) this;
        if (player != null) {
            if (player.getMainHandItem().getItem() == ModItems.STALKER_KNIFE_OFFHAND) {
                if (cir.getReturnValue() == HumanoidArm.RIGHT) {
                    cir.setReturnValue(HumanoidArm.LEFT);
                } else {
                    cir.setReturnValue(HumanoidArm.RIGHT);
                }
            }
        }
    }

}
