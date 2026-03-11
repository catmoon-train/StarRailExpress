package org.agmas.noellesroles.mixin.roles.insanekiller;

import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@Mixin(ShopEntry.class)
public abstract class InsaneShopMixin {
    @Shadow
    @Final
    private ItemStack stack;

    @Inject(method = "canBuy", at = @At("HEAD"), cancellable = true)
    private void cantBuy(@NotNull Player player, CallbackInfoReturnable<Boolean> cir) {
        if (GameWorldComponent.KEY.get(player.level()).isRole(player,
                ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
            var ikpc = InsaneKillerPlayerComponent.KEY.get(player);
            if(ikpc.inNearDeath()){
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
