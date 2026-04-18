package org.agmas.noellesroles.mixin.roles.mercenary;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameUtils.class)
public class MercenaryConfirmMixin {
    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Z)V", at = @At("HEAD"), cancellable = true)
    private static void mercenaryConfirm(Player victim, boolean spawnBody, Player killer, ResourceLocation identifier,
            boolean force, CallbackInfo ci) {
        if (killer == null || victim == null) {
            return;
        }

        var world = victim.level();
        if (world == null) {
            return;
        }

        var gameWorldComponent = SREGameWorldComponent.KEY.get(world);
        if (gameWorldComponent == null) {
            return;
        }

        if (!gameWorldComponent.isRole(killer, ModRoles.MERCENARY)) {
            return;
        }

        var mercenary = MercenaryPlayerComponent.KEY.get(killer);
        if (mercenary == null) {
            return;
        }

        boolean isContractTarget = mercenary.isContractTarget(victim);
        boolean isForcedTarget = mercenary.isForcedTarget(victim);
        if (!isContractTarget && !isForcedTarget) {
            killer.displayClientMessage(
                    Component.translatable("message.noellesroles.mercenary.invalid_target"),
                    true);
            ci.cancel();
        }
    }
}
