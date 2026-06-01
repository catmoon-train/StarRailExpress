package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CustomNarratorPacket(Component content, boolean shouldInterrupt) implements CustomPacketPayload {
    public static final Type<CustomNarratorPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "custom_narrator"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CustomNarratorPacket> CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, CustomNarratorPacket::content, ByteBufCodecs.BOOL,
            CustomNarratorPacket::shouldInterrupt, CustomNarratorPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}