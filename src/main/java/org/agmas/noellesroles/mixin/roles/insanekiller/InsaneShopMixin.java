package org.agmas.noellesroles.mixin.roles.insanekiller;

import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;

@Mixin(ShopEntry.class)
public abstract class InsaneShopMixin {

    @Inject(method = "canBuy", at = @At("HEAD"), cancellable = true)
    private void cantBuy(@NotNull Player player, CallbackInfoReturnable<Boolean> cir) {
        if (StarGameWorldComponent.KEY.get(player.level()).isRole(player,
                ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
            var ikpc = InsaneKillerPlayerComponent.KEY.get(player);
            if(ikpc.inNearDeath()){
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
