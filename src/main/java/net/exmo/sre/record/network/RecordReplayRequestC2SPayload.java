package net.exmo.sre.record.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 -> 服务端：请求某一场战绩的完整回放数据。
 */
public record RecordReplayRequestC2SPayload(String matchId) implements CustomPacketPayload {
    public static final Type<RecordReplayRequestC2SPayload> ID = new Type<>(SRE.id("record_replay_request"));
    public static final StreamCodec<FriendlyByteBuf, RecordReplayRequestC2SPayload> CODEC =
            CustomPacketPayload.codec(RecordReplayRequestC2SPayload::write, RecordReplayRequestC2SPayload::new);

    private RecordReplayRequestC2SPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(64));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(matchId, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
