// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package io.wifi.starrailexpress.network.packet;

import java.util.List;
import java.util.Optional;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EditNewspaperPacket(List<String> pages, Optional<String> title) implements CustomPacketPayload {
    public static final int MAX_BYTES_PER_CHAR = 4;
    public static final StreamCodec<FriendlyByteBuf, EditNewspaperPacket> STREAM_CODEC;
    public static final Type<EditNewspaperPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "newspaper/edit"));

    static {
        STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(8192).apply(ByteBufCodecs.list(200)), EditNewspaperPacket::pages,
                ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs::optional), EditNewspaperPacket::title,
                EditNewspaperPacket::new);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
