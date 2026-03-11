package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AnnounceEndingPayload() implements CustomPacketPayload {
    public static final Type<AnnounceEndingPayload> ID = new Type<>(SRE.id("announceending"));
    public static final StreamCodec<FriendlyByteBuf, AnnounceEndingPayload> CODEC = StreamCodec.unit(new AnnounceEndingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}