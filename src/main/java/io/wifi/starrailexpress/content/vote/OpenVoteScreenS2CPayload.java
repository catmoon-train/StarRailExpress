package io.wifi.starrailexpress.content.vote;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.*;

/**
 * S2C – 服务端向客户端发送投票会话数据并请求打开投票界面。
 * <p>
 * 服务端在投票开始时或有玩家加入时发送此包。
 */
public record OpenVoteScreenS2CPayload(
        String sessionId,
        String title,
        List<VoteOption> options,
        boolean showResults,
        boolean allowRevote,
        long endTimeMillis,
        Map<Integer, Integer> voteCounts,
        int playerVote
) implements CustomPacketPayload {

    public static final Type<OpenVoteScreenS2CPayload> ID =
            new Type<>(SRE.id("open_vote_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVoteScreenS2CPayload> CODEC =
            StreamCodec.ofMember(OpenVoteScreenS2CPayload::encode, OpenVoteScreenS2CPayload::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(sessionId);
        buf.writeUtf(title);
        buf.writeVarInt(options.size());
        for (VoteOption opt : options) opt.encode(buf);
        buf.writeBoolean(showResults);
        buf.writeBoolean(allowRevote);
        buf.writeLong(endTimeMillis);
        buf.writeVarInt(voteCounts.size());
        for (Map.Entry<Integer, Integer> e : voteCounts.entrySet()) {
            buf.writeVarInt(e.getKey());
            buf.writeVarInt(e.getValue());
        }
        buf.writeVarInt(playerVote);
    }

    private static OpenVoteScreenS2CPayload decode(RegistryFriendlyByteBuf buf) {
        String sessionId = buf.readUtf();
        String title = buf.readUtf();
        int optCount = buf.readVarInt();
        List<VoteOption> options = new ArrayList<>(optCount);
        for (int i = 0; i < optCount; i++) options.add(VoteOption.decode(buf));
        boolean showResults = buf.readBoolean();
        boolean allowRevote = buf.readBoolean();
        long endTimeMillis = buf.readLong();
        int countSize = buf.readVarInt();
        Map<Integer, Integer> voteCounts = new LinkedHashMap<>(countSize);
        for (int i = 0; i < countSize; i++) voteCounts.put(buf.readVarInt(), buf.readVarInt());
        int playerVote = buf.readVarInt();
        return new OpenVoteScreenS2CPayload(sessionId, title, options, showResults, allowRevote,
                endTimeMillis, voteCounts, playerVote);
    }

    @Override
    public Type<OpenVoteScreenS2CPayload> type() {
        return ID;
    }
}
