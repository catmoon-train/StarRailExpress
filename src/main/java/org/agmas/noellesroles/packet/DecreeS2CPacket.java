package org.agmas.noellesroles.packet;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 仅发送领域入场、离场与低频校准，不逐帧广播。 */
public record DecreeS2CPacket(UUID casterId, String decreeId, int remainingTicks, boolean impact)
        implements CustomPacketPayload {
    public static final Type<DecreeS2CPacket> ID = new Type<>(Noellesroles.id("decree_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DecreeS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUUID(packet.casterId);
                buf.writeUtf(packet.decreeId, 32);
                buf.writeVarInt(packet.remainingTicks);
                buf.writeBoolean(packet.impact);
            }, buf -> new DecreeS2CPacket(buf.readUUID(), buf.readUtf(32), buf.readVarInt(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
