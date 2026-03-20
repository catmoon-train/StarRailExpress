package org.agmas.noellesroles.mixin.roles.executioner;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

@Mixin(GameUtils.class)
public class ExecutionerConfirmMixin {
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V", at = @At("HEAD"), cancellable = true)
    private static void executionerConfirm(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier,
            boolean force,
            CallbackInfo ci) {
        final var world = victim.level();
        if (world == null)
            return;
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
        if (gameWorldComponent == null)
            return;

        for (UUID uuid : gameWorldComponent.getAllWithRole(ModRoles.EXECUTIONER)) {
            Player executioner = world.getPlayerByUUID(uuid);
            if (executioner == null)
                continue;
            // boolean invalidKill = false;
            // if (killer != null) {
            // if (gameWorldComponent.getRole(killer).canUseKiller()) invalidKill = true;
            // }
            ExecutionerPlayerComponent executionerPlayerComponent = ExecutionerPlayerComponent.KEY.get(executioner);
            SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                    .get(executioner);
            if (executionerPlayerComponent.target != null
                    && executionerPlayerComponent.target.equals(victim.getUUID())) {
                executionerPlayerComponent.assignRandomTarget();
                if (killer != null && killer.getUUID().equals(uuid)) {
                    playerShopComponent.setBalance(playerShopComponent.balance - 25);
                } else {
                    playerShopComponent.setBalance(playerShopComponent.balance + 50);
                }
                executionerPlayerComponent.sync();
                playerShopComponent.sync();
            }
        }
        if (killer == null)
            return;
        final var role = gameWorldComponent.getRole(killer);
        if (role == null)
            return;
        if (killer != null) {
            if (victim != null) {
                if (role.getIdentifier().equals(ModRoles.EXECUTIONER_ID)) {

                    if (!ExecutionerPlayerComponent.KEY.get(killer).target.equals(victim.getUUID())) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}