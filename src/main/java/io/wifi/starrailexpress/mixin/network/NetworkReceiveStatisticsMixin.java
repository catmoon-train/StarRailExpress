package io.wifi.starrailexpress.mixin.network;

import io.netty.channel.ChannelHandlerContext;
import io.wifi.starrailexpress.network.NetworkStatistics;
import io.wifi.starrailexpress.network.NetworkUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class NetworkReceiveStatisticsMixin {


    @Shadow @Nullable public abstract PacketListener getPacketListener();

    /**
     * 拦截数据包接收方法，记录接收的网络包统计信息
     */
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", 
            at = @At("HEAD"))
    private void onReceivePacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        try {
            // 获取数据包大小
            long packetSize = NetworkUtils.estimatePacketSize(packet);
            
            // 确定数据包类型
            String packetId = packet.getClass().getSimpleName();

            // 对于自定义载荷包，尝试获取更具体的信息
            if (packet instanceof ServerboundCustomPayloadPacket customPayloadPacket) {
                try {
                    // 获取payload的ID
                    var payload = customPayloadPacket.payload();
                    if (payload != null) {
                        var type = payload.type();
                        if (type != null) {
                            ResourceLocation id = type.id();
                            if (id != null) {
                                packetId = id.toString();
                            }
                        }
                    }
                } catch (Exception e) {
                    // 如果获取ID失败，使用默认方法
                    packetId = "custom_payload";
                }
            }
            
            // 获取源玩家名称（如果可能）
            String sourcePlayer = null;
            Connection connection = (Connection) (Object) this;
            if (connection.isConnected() && connection.getPacketListener() != null) {
                // 如果连接有玩家监听器，尝试获取玩家名
                if (connection.getPacketListener() instanceof net.minecraft.server.network.ServerGamePacketListenerImpl listener) {
                    if (listener.player != null) {
                        sourcePlayer = listener.player.getName().getString();
                    }
                }
            }
            
            // 记录统计信息
            if (!(getPacketListener() instanceof ServerGamePacketListenerImpl)) {
                // 客户端从服务器接收
                NetworkStatistics.getInstance().recordPacketReceive(packetId, packetSize, net.minecraft.network.protocol.PacketFlow.SERVERBOUND, sourcePlayer);
            } else {
                // 服务器从客户端接收
                NetworkStatistics.getInstance().recordPacketReceive(packetId, packetSize, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND, sourcePlayer);
            }
            
        } catch (Exception e) {
            // 避免统计代码影响正常游戏运行
            // 可以选择性地记录错误日志
        }
    }
}