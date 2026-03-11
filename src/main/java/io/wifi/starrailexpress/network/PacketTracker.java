package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.cca.NetworkStatsComponent;
import io.wifi.starrailexpress.cca.PlayerStatsComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * 网络包跟踪器，用于统计发送和接收的数据包数量及大小
 */
public class PacketTracker {
    
    /**
     * 包装服务器向客户端发送数据包的方法，添加统计功能
     */
    public static <T extends CustomPacketPayload> void sendToClient(ServerPlayer player, T payload) {
        // 计算包大小
        long size = calculatePayloadSize(payload);

        // 记录统计信息，包含发包对象（目标玩家）
        NetworkStatistics.getInstance().recordPacketSend(payload.type().id(), size, PacketFlow.CLIENTBOUND, player.getName().getString());
        
        // 更新玩家的网络统计组件（如果存在）
        try {
//            NetworkStatsComponent.KEY.get(player).incrementPacketsSent(payload.type().id().toString(), size);
//            // 新增：记录发送给特定玩家的数据包
//            NetworkStatsComponent.KEY.get(player).incrementPacketsSentToPlayer(player.getName().getString(), payload.type().id().toString(), size);
        } catch (Exception e) {
            // 如果组件不存在或出错，忽略
        }
        
        // 发送数据包
        ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * 包装服务器向多个客户端发送数据包的方法，添加统计功能
     */
    public static <T extends CustomPacketPayload> void sendToClients(Iterable<ServerPlayer> players, T payload) {
        long size = calculatePayloadSize(payload);
        
        for (ServerPlayer player : players) {
            // 添加CCA前缀
            String packetIdWithPrefix = "CCA_" + payload.type().id();
            
            // 记录统计信息，包含发包对象（目标玩家）
            NetworkStatistics.getInstance().recordPacketSend(packetIdWithPrefix, size, PacketFlow.CLIENTBOUND, player.getName().getString());
            
            // 更新玩家的网络统计组件
            try {
//                NetworkStatsComponent.KEY.get(player).incrementPacketsSent(packetIdWithPrefix, size);
//                // 新增：记录发送给特定玩家的数据包
//                NetworkStatsComponent.KEY.get(player).incrementPacketsSentToPlayer(player.getName().getString(), packetIdWithPrefix, size);
            } catch (Exception e) {
                // 如果组件不存在或出错，忽略
            }
        }
        
        // 发送数据包到所有玩家
        players.forEach(
                serverPlayer -> ServerPlayNetworking.send(serverPlayer, payload)
        );

    }
    
    /**
     * 计算CustomPacketPayload的实际大小（近似值）
     */
    private static long calculatePayloadSize(@NotNull CustomPacketPayload payload) {
//        // 使用一个足够大的缓冲区来编码负载
//        ByteBuf buffer = Unpooled.buffer();
//        RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY);
//
//        try {
//            // 编码负载到缓冲区
//            PayloadTypeRegistryImpl.PLAY_S2C.get(payload.type()).codec().encode(friendlyByteBuf, payload);
//
//            payload.type().codec().encode(friendlyBuf, payload);
//            return friendlyBuf.readableBytes();
//        } catch (Exception e) {
//            // 如果编码失败，返回一个合理的默认大小
            return 64; // 默认大小为64字节
        }
//    }
    

    
    /**
     * 手动记录接收到的数据包（由各个接收器调用）
     */
    public static void recordReceivedPacket(ResourceLocation packetId, long size, PacketFlow flow) {
        NetworkStatistics.getInstance().recordPacketReceive(packetId, size, flow);
    }
    
    /**
     * 手动记录接收到的数据包（由各个接收器调用）- 字符串版本
     */
    public static void recordReceivedPacket(String packetId, long size, PacketFlow flow, String sourcePlayer) {
        NetworkStatistics.getInstance().recordPacketReceive(packetId, size, flow, sourcePlayer);
    }
    

    /**
     * 手动记录接收到的数据包（由各个接收器调用）- 不指定发包对象
     */
    public static void recordReceivedPacket(String packetId, long size, PacketFlow flow) {
        NetworkStatistics.getInstance().recordPacketReceive(packetId, size, flow);
    }
}