package net.exmo.sre.record.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 -> 服务端：请求最近的全局战绩列表。
 */
public record RecordListRequestC2SPayload(int limit) implements CustomPacketPayload {
    public static final Type<RecordListRequestC2SPayload> ID = new Type<>(SRE.id("record_list_request"));
    public static final StreamCodec<FriendlyByteBuf, RecordListRequestC2SPayload> CODEC =
            CustomPacketPayload.codec(RecordListRequestC2SPayload::write, RecordListRequestC2SPayload::new);

    private RecordListRequestC2SPayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(limit);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
