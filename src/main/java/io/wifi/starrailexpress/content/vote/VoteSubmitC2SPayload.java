package io.wifi.starrailexpress.content.vote;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S – 客户端向服务端提交一票。
 */
public record VoteSubmitC2SPayload(String sessionId, int optionIndex) implements CustomPacketPayload {

    public static final Type<VoteSubmitC2SPayload> ID =
            new Type<>(SRE.id("vote_submit"));

    public static final StreamCodec<FriendlyByteBuf, VoteSubmitC2SPayload> CODEC =
            StreamCodec.ofMember(VoteSubmitC2SPayload::encode, VoteSubmitC2SPayload::decode);

    private void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sessionId);
        buf.writeVarInt(optionIndex);
    }

    private static VoteSubmitC2SPayload decode(FriendlyByteBuf buf) {
        return new VoteSubmitC2SPayload(buf.readUtf(), buf.readVarInt());
    }

    @Override
    public Type<VoteSubmitC2SPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<VoteSubmitC2SPayload> {
        @Override
        public void receive(VoteSubmitC2SPayload payload, ServerPlayNetworking.Context context) {
            ServerPlayer player = context.player();
            VoteManager.getInstance().handleVote(player, payload.sessionId(), payload.optionIndex());
        }
    }
}
