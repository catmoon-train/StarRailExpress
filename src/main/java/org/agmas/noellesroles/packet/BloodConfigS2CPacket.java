package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record BloodConfigS2CPacket(Boolean enabled) implements CustomPacketPayload {
    public static final ResourceLocation BROADCASTER_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "blood_config_enabled");
    public static final Type<BloodConfigS2CPacket> ID = new Type<>(BROADCASTER_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BloodConfigS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeBoolean(packet.enabled());
            },
            buf -> new BloodConfigS2CPacket(buf.readBoolean()));

    public BloodConfigS2CPacket(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}