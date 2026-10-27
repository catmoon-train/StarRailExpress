package org.agmas.noellesroles.mixin.network;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.game.dio.RoadRollerAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 下砸期间位置由服务端轨迹接管，忽略客户端移动，避免穿墙移动回滚。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RoadRollerMovementMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void noellesroles$roadRollerMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (RoadRollerAttack.isActive(player.getUUID())) ci.cancel();
    }
}
