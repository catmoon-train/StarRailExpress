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