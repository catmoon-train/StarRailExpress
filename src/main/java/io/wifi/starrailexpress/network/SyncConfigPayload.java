package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncConfigPayload(String configId, String content) implements CustomPacketPayload {
    public static final Type<SyncConfigPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "sync_config"));
    public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec
            .ofMember(SyncConfigPayload::encode, SyncConfigPayload::decode);

    public static SyncConfigPayload decode(FriendlyByteBuf buf) {
        return new SyncConfigPayload(buf.readUtf(), buf.readUtf());
    }

    public static void encode(SyncConfigPayload payload, FriendlyByteBuf buf) {
        buf.writeUtf(payload.configId);
        buf.writeUtf(payload.content);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}