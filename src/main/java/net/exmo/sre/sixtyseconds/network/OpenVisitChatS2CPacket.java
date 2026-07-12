package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开与 partnerName 的拜访对话窗。 */
public record OpenVisitChatS2CPacket(String partnerName) implements CustomPacketPayload {
    public static final Type<OpenVisitChatS2CPacket> ID = new Type<>(Noellesroles.id("open_visit_chat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVisitChatS2CPacket> CODEC =
            StreamCodec.ofMember(OpenVisitChatS2CPacket::encode, OpenVisitChatS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(partnerName);
    }

    public static OpenVisitChatS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenVisitChatS2CPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
