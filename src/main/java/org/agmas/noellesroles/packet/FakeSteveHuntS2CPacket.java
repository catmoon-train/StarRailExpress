package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** Enables or clears the all-client presentation used by the Fake Steve hunt. */
public record FakeSteveHuntS2CPacket(boolean active) implements CustomPacketPayload {
    public static final Type<FakeSteveHuntS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fake_steve_hunt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakeSteveHuntS2CPacket> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeBoolean(packet.active),
            buf -> new FakeSteveHuntS2CPacket(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
