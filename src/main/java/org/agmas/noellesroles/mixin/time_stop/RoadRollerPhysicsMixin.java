package org.agmas.noellesroles.mixin.time_stop;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.killer.DIORoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版 Player.tick 会重设 noPhysics，需在实际运动之前重新接管碰撞。 */
@Mixin(Player.class)
public abstract class RoadRollerPhysicsMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void noellesroles$roadRollerPhysics(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        DIORoleData data = RoleData.getNullable(DIORoleData.class, player);
        if (data != null && data.roadRollerAttacking) {
            player.noPhysics = true;
            player.setNoGravity(true);
            player.fallDistance = 0;
        }
    }
}
