package io.wifi.starrailexpress.content.vote;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S2C – 定期推送当前投票票数更新。
 * <p>
 * 仅在 {@code showResults = true} 时服务端才发送此包。
 */
public record UpdateVoteCountsS2CPayload(
        String sessionId,
        Map<Integer, Integer> voteCounts,
        long endTimeMillis
) implements CustomPacketPayload {

    public static final Type<UpdateVoteCountsS2CPayload> ID =
            new Type<>(SRE.id("update_vote_counts"));

    public static final StreamCodec<FriendlyByteBuf, UpdateVoteCountsS2CPayload> CODEC =
            StreamCodec.ofMember(UpdateVoteCountsS2CPayload::encode, UpdateVoteCountsS2CPayload::decode);

    private void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sessionId);
        buf.writeLong(endTimeMillis);
        buf.writeVarInt(voteCounts.size());
        for (Map.Entry<Integer, Integer> e : voteCounts.entrySet()) {
            buf.writeVarInt(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static UpdateVoteCountsS2CPayload decode(FriendlyByteBuf buf) {
        String sessionId = buf.readUtf();
        long endTimeMillis = buf.readLong();
        int size = buf.readVarInt();
        Map<Integer, Integer> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) map.put(buf.readVarInt(), buf.readVarInt());
        return new UpdateVoteCountsS2CPayload(sessionId, map, endTimeMillis);
    }

    @Override
    public Type<UpdateVoteCountsS2CPayload> type() {
        return ID;
    }
}
