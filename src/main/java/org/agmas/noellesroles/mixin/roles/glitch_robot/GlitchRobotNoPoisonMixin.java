package org.agmas.noellesroles.mixin.roles.glitch_robot;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerPoisonComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(StarPlayerPoisonComponent.class)
public abstract class GlitchRobotNoPoisonMixin {

    @Shadow private Player player;

    @Inject(method = "setPoisonTicks", at = @At("HEAD"), cancellable = true)
    private void glitchRobotNoPoison(int ticks, UUID poisoner, CallbackInfo ci) {
        StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(this.player.level());
        if (gameWorld.isRole(this.player, ModRoles.GLITCH_ROBOT)) {
            ci.cancel();
        }
    }
}