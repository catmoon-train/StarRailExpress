package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record ClearBloodParticlesS2CPacket() implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "clear_blood_particles");
    public static final Type<ClearBloodParticlesS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearBloodParticlesS2CPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {},
            buf -> new ClearBloodParticlesS2CPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
