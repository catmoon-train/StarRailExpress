package io.wifi.starrailexpress.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class NetworkUtils {

    /**
     * 估算数据包的大小（以字节为单位）
     *
     * @param packet 要估算大小的数据包
     * @return 数据包大小（字节）
     */
    public static long estimatePacketSize(Packet<?> packet) {
        try {
            // 对于自定义载荷包，尝试获取其实际大小
            if (packet instanceof ClientboundCustomPayloadPacket) {
                return estimateCustomPayloadSize(((ClientboundCustomPayloadPacket) packet).payload());
            } else if (packet instanceof ServerboundCustomPayloadPacket) {
                return estimateCustomPayloadSize(((ServerboundCustomPayloadPacket) packet).payload());
            }

            // 对于其他类型的数据包，使用序列化方法估算大小
            return estimateGenericPacketSize(packet);
        } catch (Exception e) {
            // 如果估算失败，返回一个默认值
            return 64; // 默认64字节
        }
    }

    /**
     * 估算自定义载荷的大小
     */
    private  static <T extends CustomPacketPayload> long  estimateCustomPayloadSize(T payload) {
        if (payload == null) {
            return 0;
        }

        try {
            // 创建一个临时缓冲区来编码载荷
            ByteBuf buffer = Unpooled.buffer();
            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY);
            // 使用payload的write方法进行编码
           // PayloadTypeRegistryImpl.PLAY_S2C.get(payload.type()).codec().encode(friendlyByteBuf, payload);
            int size = buffer.readableBytes();
            buffer.release(); // 释放缓冲区以避免内存泄漏
            
            return size;
        } catch (Exception e) {
            // 如果编码失败，返回一个合理的默认值
            return 128; // 默认128字节
        }
    }
    
    /**
     * 估算一般数据包的大小
     * 这是一个近似估算，因为Minecraft的数据包没有直接提供大小信息
     */
    private static long estimateGenericPacketSize(Packet<?> packet) {
        // 由于Minecraft的数据包没有直接提供大小信息，
        // 我们只能返回一个合理的估算值
        // 在实际应用中，可能需要更复杂的序列化方法来准确测量
        return 128; // 默认128字节，这是一个保守估计
    }
}