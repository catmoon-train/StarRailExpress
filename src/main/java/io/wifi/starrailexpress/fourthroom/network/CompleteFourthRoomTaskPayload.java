package io.wifi.starrailexpress.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.fourthroom.game.FourthRoomGameManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record CompleteFourthRoomTaskPayload() implements CustomPacketPayload {
    public static final Type<CompleteFourthRoomTaskPayload> ID = new Type<>(SRE.id("fourth_room_complete_task"));
    public static final StreamCodec<FriendlyByteBuf, CompleteFourthRoomTaskPayload> CODEC = StreamCodec.unit(new CompleteFourthRoomTaskPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<CompleteFourthRoomTaskPayload> {
        @Override
        public void receive(@NotNull CompleteFourthRoomTaskPayload payload, ServerPlayNetworking.@NotNull Context context) {
            FourthRoomGameManager.of(context.player().serverLevel()).taskScheduler().completeTask(context.player().getUUID());
        }
    }
}