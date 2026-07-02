package org.agmas.noellesroles.mixin.client.general;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithAssassinPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class WraithVisibilityMixin {
    @Inject(method = "isInvisibleTo", at = @At("RETURN"), cancellable = true)
    private void noellesroles$wraithVisibility(Player viewer, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player target) || !target.hasEffect(ModEffects.WRAITH_DIMENSION)) {
            return;
        }
        if (target.hasEffect(ModEffects.WRAITH_MANIFEST)) {
            cir.setReturnValue(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || viewer != mc.player || SREClient.gameComponent == null) {
            return;
        }
        if (!SREClient.gameComponent.isRole(target, ModRoles.WRAITH_ASSASSIN)) {
            return;
        }
        cir.setReturnValue(!WraithAssassinPlayerComponent.canPerceiveWraith(viewer));
    }
}
