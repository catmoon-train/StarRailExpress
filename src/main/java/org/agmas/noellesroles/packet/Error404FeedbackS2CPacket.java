package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 终端显示服务端实际裁决，而不是把客户端的下令意图当成成功。 */
public record Error404FeedbackS2CPacket(String message) implements CustomPacketPayload {
    public static final Type<Error404FeedbackS2CPacket> ID = new Type<>(Noellesroles.id("error404_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Error404FeedbackS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeUtf(packet.message(), 64), buf -> new Error404FeedbackS2CPacket(buf.readUtf(64)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
