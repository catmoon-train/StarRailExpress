package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record StoreBuyPayload(int index) implements CustomPacketPayload {
    public static final Type<StoreBuyPayload> ID = new Type<>(SRE.id("storebuy"));
    public static final StreamCodec<FriendlyByteBuf, StoreBuyPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT, StoreBuyPayload::index, StoreBuyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<StoreBuyPayload> {
        @Override
        public void receive(@NotNull StoreBuyPayload payload, ServerPlayNetworking.@NotNull Context context) {
            SREPlayerShopComponent.KEY.get(context.player()).tryBuy(payload.index());
        }
    }
}