package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 设陷者技能网络包
 * 用于客户端向服务端发送放置陷阱请求
 */
public record TrapperC2SPacket() implements CustomPacketPayload {
    
    public static final Type<TrapperC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "trapper_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, TrapperC2SPacket> CODEC = StreamCodec.ofMember(
        (packet, buf) -> {
            // 无需写入数据，只是触发技能
        },
        buf -> new TrapperC2SPacket()
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}