package io.wifi.starrailexpress.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import io.wifi.starrailexpress.client.gui.screen.ingame.FourthRoomPeekDeckScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record OpenFourthRoomPeekDeckPayload() implements CustomPacketPayload {
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

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.client().execute(() -> {
            var client = context.client();
            if (client.level == null || client.player == null) {
                return;
            }
            if (!FourthRoomClientState.snapshot().active()
                    || FourthRoomClientState.snapshot().viewer().peekCards().isEmpty()) {
                return;
            }
            if (client.screen instanceof FourthRoomPeekDeckScreen) {
                return;
            }
            client.setScreen(new FourthRoomPeekDeckScreen(client.screen));
        }));
    }
}