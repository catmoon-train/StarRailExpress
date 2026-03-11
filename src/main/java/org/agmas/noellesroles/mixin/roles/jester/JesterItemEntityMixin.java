package org.agmas.noellesroles.mixin.roles.jester;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class JesterItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void jesterJest(Player player, CallbackInfo ci) {
        try {


            GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.level());
            if (!player.isCreative() && gameWorldComponent.isRole(player, ModRoles.JESTER)) {
                ci.cancel();
            }
        }catch (Exception exception){

        }
    }
}
