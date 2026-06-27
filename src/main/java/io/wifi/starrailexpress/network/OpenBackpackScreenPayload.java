package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBackpackScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenBackpackScreenPayload> ID = new Type<>(SRE.id("open_backpack_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenBackpackScreenPayload> CODEC =
            CustomPacketPayload.codec(OpenBackpackScreenPayload::encode, OpenBackpackScreenPayload::decode);

    public static final OpenBackpackScreenPayload INSTANCE = new OpenBackpackScreenPayload();

    public static void encode(OpenBackpackScreenPayload payload, FriendlyByteBuf buf) {
    }

    public static OpenBackpackScreenPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    @Override
    public Type<OpenBackpackScreenPayload> type() {
        return ID;
    }
}
