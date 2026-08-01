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

package io.wifi.starrailexpress.network;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
public class NetworkHandler {
    // 优化：减少发送距离从96格到64格，减少网络占用
    private static final int NETWORK_RANGE = 64;
    private static final int NETWORK_RANGE_SQUARED = NETWORK_RANGE * NETWORK_RANGE;
    
    public static void sendToNearBy(Level world, BlockPos pos, CustomPacketPayload toSend) {
        if (world instanceof ServerLevel) {
            ServerLevel serverWorld = (ServerLevel) world;

            serverWorld.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < NETWORK_RANGE_SQUARED)
                    .forEach(p -> PacketTracker.sendToClient(p, toSend));
        }
    }

    public static void sendToClientPlayer(CustomPacketPayload toSend, ServerPlayer player) {
        PacketTracker.sendToClient(player, toSend);
    }
    public static void sendToServer(CustomPacketPayload toSend) {
        ClientPlayNetworking.send(toSend);
    }
}