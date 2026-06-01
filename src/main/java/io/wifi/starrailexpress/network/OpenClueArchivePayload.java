package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenClueArchivePayload() implements CustomPacketPayload {
    public static final Type<OpenClueArchivePayload> ID = new Type<>(SRE.id("open_clue_archive"));
    public static final StreamCodec<FriendlyByteBuf, OpenClueArchivePayload> CODEC =
            CustomPacketPayload.codec(OpenClueArchivePayload::encode, OpenClueArchivePayload::decode);
    public static final OpenClueArchivePayload INSTANCE = new OpenClueArchivePayload();

    public static void encode(OpenClueArchivePayload payload, FriendlyByteBuf buf) {
    }

    public static OpenClueArchivePayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    @Override
    public Type<OpenClueArchivePayload> type() {
        return ID;
    }
}
