package org.agmas.noellesroles.game.modes.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.jetbrains.annotations.NotNull;

public record EndTurnPayload() implements CustomPacketPayload {
    public static final Type<EndTurnPayload> ID = new Type<>(SRE.id("fourth_room_end_turn"));
    public static final StreamCodec<FriendlyByteBuf, EndTurnPayload> CODEC = StreamCodec.unit(new EndTurnPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<EndTurnPayload> {
        @Override
        public void receive(@NotNull EndTurnPayload payload, ServerPlayNetworking.@NotNull Context context) {
            FourthRoomGameManager.of(context.player().serverLevel()).endTurn(context.player().getUUID());
        }
    }
}