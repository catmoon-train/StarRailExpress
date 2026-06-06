package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record SkincrawlerC2SPacket() implements CustomPacketPayload {
    public static final Type<SkincrawlerC2SPacket> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "skincrawler_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkincrawlerC2SPacket> CODEC = StreamCodec.unit(new SkincrawlerC2SPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
