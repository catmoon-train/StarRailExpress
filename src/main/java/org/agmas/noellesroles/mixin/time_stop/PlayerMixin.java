package org.agmas.noellesroles.mixin.time_stop;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.DIOPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "isSwimming",at = @At("HEAD"), cancellable = true)
    public void isSwim(CallbackInfoReturnable<Boolean> cir){
        Player player = (Player) (Object)this;
        if (GameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.DIO)){
            if (DIOPlayerComponent.KEY.get(player).isFeeding){
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

}
