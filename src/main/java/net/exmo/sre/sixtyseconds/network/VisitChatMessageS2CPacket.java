package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：拜访对话中的一条消息（sender 说 text）。 */
public record VisitChatMessageS2CPacket(String sender, String text) implements CustomPacketPayload {
    public static final Type<VisitChatMessageS2CPacket> ID = new Type<>(Noellesroles.id("visit_chat_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisitChatMessageS2CPacket> CODEC =
            StreamCodec.ofMember(VisitChatMessageS2CPacket::encode, VisitChatMessageS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(sender);
        buf.writeUtf(text);
    }

    public static VisitChatMessageS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new VisitChatMessageS2CPacket(buf.readUtf(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
