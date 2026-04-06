package io.wifi.starrailexpress.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.fourthroom.game.FourthRoomGameManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record CardPlayPayload(String cardId, String targetId) implements CustomPacketPayload {
    public static final Type<CardPlayPayload> ID = new Type<>(SRE.id("fourth_room_card_play"));
    public static final StreamCodec<FriendlyByteBuf, CardPlayPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CardPlayPayload::cardId,
            ByteBufCodecs.STRING_UTF8,
            CardPlayPayload::targetId,
            CardPlayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<CardPlayPayload> {
        @Override
        public void receive(@NotNull CardPlayPayload payload, ServerPlayNetworking.@NotNull Context context) {
            UUID target = payload.targetId().isBlank() ? null : UUID.fromString(payload.targetId());
            FourthRoomGameManager.of(context.player().serverLevel()).playCard(context.player().getUUID(), payload.cardId(), target);
        }
    }
}