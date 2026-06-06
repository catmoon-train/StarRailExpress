package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record EmbalmerC2SPacket() implements CustomPacketPayload {
    public static final Type<EmbalmerC2SPacket> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "embalmer_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EmbalmerC2SPacket> CODEC = StreamCodec.unit(new EmbalmerC2SPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
