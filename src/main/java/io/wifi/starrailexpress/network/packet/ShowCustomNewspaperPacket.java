// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package io.wifi.starrailexpress.network.packet;

import java.util.List;
import java.util.Optional;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShowCustomNewspaperPacket(List<Component> pages, Optional<Component> title, Optional<Component> author)
        implements CustomPacketPayload {
    public static final int MAX_BYTES_PER_CHAR = 4;
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowCustomNewspaperPacket> STREAM_CODEC;
    public static final Type<ShowCustomNewspaperPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "newspaper/show"));

    static {
        STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(200)), ShowCustomNewspaperPacket::pages,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs::optional), ShowCustomNewspaperPacket::title,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs::optional), ShowCustomNewspaperPacket::author,
                ShowCustomNewspaperPacket::new);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
