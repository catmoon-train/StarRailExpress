package io.wifi.starrailexpress.content.vote;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S2C – 投票结束，通知客户端关闭投票界面并展示最终结果。
 */
public record CloseVoteScreenS2CPayload(
        String sessionId,
        Map<Integer, Integer> finalCounts
) implements CustomPacketPayload {

    public static final Type<CloseVoteScreenS2CPayload> ID =
            new Type<>(SRE.id("close_vote_screen"));

    public static final StreamCodec<FriendlyByteBuf, CloseVoteScreenS2CPayload> CODEC =
            StreamCodec.ofMember(CloseVoteScreenS2CPayload::encode, CloseVoteScreenS2CPayload::decode);

    private void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sessionId);
        buf.writeVarInt(finalCounts.size());
        for (Map.Entry<Integer, Integer> e : finalCounts.entrySet()) {
            buf.writeVarInt(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    private static CloseVoteScreenS2CPayload decode(FriendlyByteBuf buf) {
        String sessionId = buf.readUtf();
        int size = buf.readVarInt();
        Map<Integer, Integer> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) map.put(buf.readVarInt(), buf.readVarInt());
        return new CloseVoteScreenS2CPayload(sessionId, map);
    }

    @Override
    public Type<CloseVoteScreenS2CPayload> type() {
        return ID;
    }
}
