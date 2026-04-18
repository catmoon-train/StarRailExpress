package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OnGameFinishedPayload() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID,
            "on_game_finished");
    public static final Type<OnGameFinishedPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OnGameFinishedPayload> CODEC;


    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {


    }

    public static OnGameFinishedPayload read(FriendlyByteBuf buf) {
        return new OnGameFinishedPayload();
    }

    static {
        CODEC = StreamCodec.ofMember(OnGameFinishedPayload::write, OnGameFinishedPayload::read);
    }
}