package org.agmas.noellesroles.mixin.roles.raven;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 渡鸦狩猎期间禁止左键近战攻击（包括击退）。
 * 击杀只能通过刀刺系统完成。
 */
@Mixin(ServerPlayer.class)
public abstract class RavenKnifeLeftClickMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void noellesroles$cancelRavenKnifeAttack(Entity target, CallbackInfo ci) {
        ServerPlayer attacker = (ServerPlayer) (Object) this;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        if (!gameWorld.isRole(attacker, ModRoles.RAVEN)) {
            return;
        }

        if (!ModComponents.RAVEN.get(attacker).isHunting()) {
            return;
        }

        // Hunting → cancel any left-click melee
        ci.cancel();
    }
}
