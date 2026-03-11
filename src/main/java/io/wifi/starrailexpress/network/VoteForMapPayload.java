package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.voting.MapVotingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record VoteForMapPayload(String mapId) implements CustomPacketPayload {
    public static final Type<VoteForMapPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("starrailexpress", "vote_for_map"));

    public static final StreamCodec<FriendlyByteBuf, VoteForMapPayload> CODEC = CustomPacketPayload.codec(
            VoteForMapPayload::write, VoteForMapPayload::new
    );

    public VoteForMapPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(mapId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Handler {
        public static void handle(VoteForMapPayload payload, ServerPlayer player) {
            // 通过投票管理器处理投票
            MapVotingManager.getInstance().voteForMap(player.getUUID(), payload.mapId());
        }
    }
}