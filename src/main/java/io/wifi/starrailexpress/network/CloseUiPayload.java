package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class CloseUiPayload implements CustomPacketPayload {
    public static final Type<CloseUiPayload> ID = new Type<>(SRE.id("close_ui"));
    public static final StreamCodec<FriendlyByteBuf, CloseUiPayload> CODEC = CustomPacketPayload.codec(CloseUiPayload::write, CloseUiPayload::new);

    public CloseUiPayload(FriendlyByteBuf friendlyByteBuf) {

    }
    public CloseUiPayload() {

    }


    public void write(FriendlyByteBuf buf) {

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
