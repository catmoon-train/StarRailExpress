package org.agmas.noellesroles.mixin.time_stop;

import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerPacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPacketBlocker {
    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket serverboundTeleportToEntityPacket, CallbackInfo ci) {
        ServerGamePacketListenerImpl instance = (ServerGamePacketListenerImpl)(Object)this;
        if (instance.player.hasEffect((ModEffects.TIME_STOP))) {
            if (TimeStopEffect.canMovePlayers.contains(instance.player.getUUID()))return;
            ci.cancel();
        }
    }
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void handleMovePlayerPacket(ServerboundMovePlayerPacket serverboundMovePlayerPacket, CallbackInfo ci) {
        ServerGamePacketListenerImpl instance = (ServerGamePacketListenerImpl)(Object)this;
        if (instance.player.hasEffect((ModEffects.TIME_STOP))) {
            if (TimeStopEffect.canMovePlayers.contains(instance.player.getUUID()))return;
            ci.cancel();
        }
    }
}
