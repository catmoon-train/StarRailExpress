package org.agmas.noellesroles.game.modes.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.jetbrains.annotations.NotNull;

public record RevealIdentityPayload() implements CustomPacketPayload {
    public static final Type<RevealIdentityPayload> ID = new Type<>(SRE.id("fourth_room_reveal_identity"));
    public static final StreamCodec<FriendlyByteBuf, RevealIdentityPayload> CODEC = StreamCodec.unit(new RevealIdentityPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<RevealIdentityPayload> {
        @Override
        public void receive(@NotNull RevealIdentityPayload payload, ServerPlayNetworking.@NotNull Context context) {
            FourthRoomGameManager.of(context.player().serverLevel()).revealOwnIdentity(context.player().getUUID());
        }
    }
}