package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CustomNarratorPacket(String content, boolean shouldInterrupt) implements CustomPacketPayload {
    public static final Type<CustomNarratorPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "custom_narrator"));
    public static final StreamCodec<FriendlyByteBuf, CustomNarratorPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUtf(packet.content());
                buf.writeBoolean(packet.shouldInterrupt());
            },
            buf -> {
                return new CustomNarratorPacket(buf.readUtf(),buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}