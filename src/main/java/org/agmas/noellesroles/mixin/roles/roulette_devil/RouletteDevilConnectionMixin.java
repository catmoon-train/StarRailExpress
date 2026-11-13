package org.agmas.noellesroles.mixin.roles.roulette_devil;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilSessions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 拦截直接使用连接层进行的技能/命令传送，保留原地位置校准。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RouletteDevilConnectionMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"), cancellable = true)
    private void rouletteDevil$connectionTeleport(double x, double y, double z, float yaw, float pitch,
            Set<RelativeMovement> relative, CallbackInfo ci) {
        if (RouletteDevilSessions.locked(player.getUUID()) && (relative.contains(RelativeMovement.X)
                || relative.contains(RelativeMovement.Y) || relative.contains(RelativeMovement.Z)
                || player.distanceToSqr(x, y, z) > .01)) ci.cancel();
    }
}
