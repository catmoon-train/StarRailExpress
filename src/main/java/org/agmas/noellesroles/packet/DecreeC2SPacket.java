package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record DecreeC2SPacket(String decreeId) implements CustomPacketPayload {
    public static final Type<DecreeC2SPacket> ID = new Type<>(Noellesroles.id("decree_cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DecreeC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeUtf(packet.decreeId, 32), buf -> new DecreeC2SPacket(buf.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
