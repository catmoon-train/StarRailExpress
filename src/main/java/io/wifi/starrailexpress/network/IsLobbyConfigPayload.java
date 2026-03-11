package io.wifi.starrailexpress.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IsLobbyConfigPayload(boolean isLobby) implements CustomPacketPayload {
    public static final Type<IsLobbyConfigPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "islobby_config"));
    public static final StreamCodec<FriendlyByteBuf, IsLobbyConfigPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, IsLobbyConfigPayload::isLobby,
            IsLobbyConfigPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public IsLobbyConfigPayload(boolean isLobby) {
        this.isLobby = isLobby;
    }

}