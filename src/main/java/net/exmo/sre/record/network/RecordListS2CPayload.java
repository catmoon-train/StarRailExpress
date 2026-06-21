package net.exmo.sre.record.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：返回全局战绩列表（精简摘要的 JSON 数组）。
 * 客户端收到后会刷新或打开战绩列表 GUI。
 */
public record RecordListS2CPayload(String json) implements CustomPacketPayload {
    public static final Type<RecordListS2CPayload> ID = new Type<>(SRE.id("record_list"));
    public static final StreamCodec<FriendlyByteBuf, RecordListS2CPayload> CODEC =
            CustomPacketPayload.codec(RecordListS2CPayload::write, RecordListS2CPayload::new);

    private RecordListS2CPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
