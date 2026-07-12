package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开与 partnerName 的交易窗（主手对主手交换）。 */
public record OpenTradeS2CPacket(String partnerName) implements CustomPacketPayload {
    public static final Type<OpenTradeS2CPacket> ID = new Type<>(Noellesroles.id("open_trade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTradeS2CPacket> CODEC =
            StreamCodec.ofMember(OpenTradeS2CPacket::encode, OpenTradeS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(partnerName);
    }

    public static OpenTradeS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenTradeS2CPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
