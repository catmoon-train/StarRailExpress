package org.agmas.noellesroles.game.modes.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class OpenFourthRoomPeekDeckPayload implements CustomPacketPayload {
    public static final Type<OpenFourthRoomPeekDeckPayload> ID = new Type<>(SRE.id("fourth_room_open_peek_deck"));
    public static final StreamCodec<FriendlyByteBuf, OpenFourthRoomPeekDeckPayload> CODEC =
            CustomPacketPayload.codec(OpenFourthRoomPeekDeckPayload::encode, OpenFourthRoomPeekDeckPayload::decode);

    public static final OpenFourthRoomPeekDeckPayload INSTANCE = new OpenFourthRoomPeekDeckPayload();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, INSTANCE);
    }

    public static void encode(OpenFourthRoomPeekDeckPayload payload, FriendlyByteBuf buf) {
    }

    public static OpenFourthRoomPeekDeckPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }
}