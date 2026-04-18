package org.agmas.noellesroles.mixin.roles.insanekiller;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShopEntry.class)
public abstract class InsaneShopMixin {

    @Inject(method = "canBuy", at = @At("HEAD"), cancellable = true)
    private void cantBuy(@NotNull Player player, CallbackInfoReturnable<Boolean> cir) {
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                ModRoles.INSANE_KILLER)) {
            var ikpc = InsaneKillerPlayerComponent.KEY.get(player);
            if(ikpc.inNearDeath()){
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
