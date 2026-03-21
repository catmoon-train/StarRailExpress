package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 设陷者切换陷阱类型网络包
 * 用于客户端向服务端发送切换陷阱类型请求
 */
public record TrapperSwitchC2SPacket() implements CustomPacketPayload {

    public static final Type<TrapperSwitchC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "trapper_switch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrapperSwitchC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                // 无需写入数据，只是切换类型
            },
            buf -> new TrapperSwitchC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
