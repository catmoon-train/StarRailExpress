package org.agmas.noellesroles.mixin.roles.executioner;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.network.original.GunShootPayload;

import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;

@Mixin(GunShootPayload.Receiver.class)
public class NoTargetBackfireMixin {
    @WrapOperation(method = "receive", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/cca/GameWorldComponent;isInnocent(Lnet/minecraft/world/entity/player/Player;)Z", ordinal = 0))
    private boolean noBackfire(GameWorldComponent instance, Player player, Operation<Boolean> original) {
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.level());
        for (UUID uuid : gameWorldComponent.getAllWithRole(ModRoles.EXECUTIONER)) {
            Player executioner = player.level().getPlayerByUUID(uuid);
            if (executioner == null) continue;
            ExecutionerPlayerComponent executionerPlayerComponent = ExecutionerPlayerComponent.KEY.get(executioner);
            if (executionerPlayerComponent.target != null && executionerPlayerComponent.target.equals(player.getUUID())) {
                return false;
            }
        }
        if (gameWorldComponent.isRole(player, ModRoles.VOODOO) && NoellesRolesConfig.HANDLER.instance().voodooShotLikeEvil) {
            return false;
        }
        return original.call(instance,player);
    }
}
