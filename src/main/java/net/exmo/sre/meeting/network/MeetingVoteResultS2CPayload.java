package net.exmo.sre.meeting.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * S2C：投票结果通报。会议投票结束后下发。
 *
 * @param expelledPlayerName 被驱逐的玩家名（无人被驱逐时为空字符串）
 * @param voteEntries        投票结果列表（玩家名 → 票数）
 */
public record MeetingVoteResultS2CPayload(String expelledPlayerName,
        List<VoteEntry> voteEntries) implements CustomPacketPayload {

    public static final Type<MeetingVoteResultS2CPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_vote_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeetingVoteResultS2CPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.expelledPlayerName);
                buf.writeVarInt(payload.voteEntries.size());
                for (VoteEntry entry : payload.voteEntries) {
                    buf.writeUtf(entry.playerName);
                    buf.writeVarInt(entry.voteCount);
                }
            },
            buf -> {
                String expelledName = buf.readUtf();
                int size = buf.readVarInt();
                List<VoteEntry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new VoteEntry(buf.readUtf(), buf.readVarInt()));
                }
                return new MeetingVoteResultS2CPayload(expelledName, entries);
            });

    @Override
    public Type<MeetingVoteResultS2CPayload> type() {
        return ID;
    }

    /** 单条投票结果（玩家名 → 票数）。 */
    public record VoteEntry(String playerName, int voteCount) {
    }
}
