package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record PelicanReleaseC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PELICAN_RELEASE_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pelican_release");
    public static final Type<PelicanReleaseC2SPacket> ID = new Type<>(PELICAN_RELEASE_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PelicanReleaseC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {}
    public static PelicanReleaseC2SPacket read(FriendlyByteBuf buf) {
        return new PelicanReleaseC2SPacket();
    }

    static {
        CODEC = StreamCodec.ofMember(PelicanReleaseC2SPacket::write, PelicanReleaseC2SPacket::read);
    }
}
