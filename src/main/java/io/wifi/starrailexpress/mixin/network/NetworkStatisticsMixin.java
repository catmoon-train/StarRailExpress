package io.wifi.starrailexpress.mixin.network;

import io.wifi.starrailexpress.network.NetworkStatistics;
import io.wifi.starrailexpress.network.NetworkUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class NetworkStatisticsMixin {



    @Shadow @Nullable public abstract PacketListener getPacketListener();



    /**
     * 拦截数据包发送方法，记录发送的网络包统计信息
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", 
            at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, PacketSendListener sendCallback, CallbackInfo ci) {
        try {
            // 获取数据包大小
            long packetSize = NetworkUtils.estimatePacketSize(packet);
            
            // 确定数据包类型
            String packetId = packet.getClass().getSimpleName();
            
            // 对于自定义载荷包，尝试获取更具体的信息
            if (packet instanceof ClientboundCustomPayloadPacket customPayloadPacket) {
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
            
            // 获取目标玩家名称（如果可能）
            String targetPlayer = null;
            Connection packetListener = ((Connection) (Object) this);
            if (packetListener instanceof Connection conn) {
                if (conn.getPacketListener() instanceof ServerPlayer serverPlayer) {
                    targetPlayer = serverPlayer.getName().getString();
                }
            }
            
            // 记录统计信息
            if (!(getPacketListener() instanceof ServerCommonPacketListenerImpl)) {
                // 客户端发送到服务器
                NetworkStatistics.getInstance().recordPacketSend(packetId, packetSize, net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            } else {
                // 服务器发送到客户端
                NetworkStatistics.getInstance().recordPacketSend(packetId, packetSize, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND, targetPlayer);
            }
            
        } catch (Exception e) {
            // 避免统计代码影响正常游戏运行
            // LOGGER.warn("Failed to record sent packet statistics: " + e.getMessage());
        }
    }
}