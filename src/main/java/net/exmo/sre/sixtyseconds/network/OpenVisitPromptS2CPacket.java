package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/** 服务端→客户端：向目标队成员弹出“是否同意拜访”提示。 */
public record OpenVisitPromptS2CPacket(UUID visitor, String visitorName, int requestType) implements CustomPacketPayload {
    public static final Type<OpenVisitPromptS2CPacket> ID = new Type<>(Noellesroles.id("open_visit_prompt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVisitPromptS2CPacket> CODEC =
            StreamCodec.ofMember(OpenVisitPromptS2CPacket::encode, OpenVisitPromptS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(visitor);
        buf.writeUtf(visitorName);
        buf.writeVarInt(requestType);
    }

    public static OpenVisitPromptS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenVisitPromptS2CPacket(buf.readUUID(), buf.readUtf(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
