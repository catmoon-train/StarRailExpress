package org.agmas.noellesroles.mixin.roles.veteran;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SERoles;

@Mixin(ServerPlayer.class)
public abstract class VeteranKnifeLeftClickMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onVeteranKnifeAttack(Entity target, CallbackInfo ci) {
        ServerPlayer attacker = (ServerPlayer) (Object) this;

        if (!(target instanceof Player targetPlayer))
            return;

        if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        if (gameWorld.isRole(attacker, SERoles.INITIATE) || gameWorld.isRole(attacker, ModRoles.VETERAN)) {
            ItemStack mainHand = attacker.getItemInHand(InteractionHand.MAIN_HAND);
            if (mainHand.is(TMMItems.KNIFE)) {
                ci.cancel();
            }
        }

    }
}