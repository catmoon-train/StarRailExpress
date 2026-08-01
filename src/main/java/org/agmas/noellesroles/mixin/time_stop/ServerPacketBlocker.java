/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.time_stop;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
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
