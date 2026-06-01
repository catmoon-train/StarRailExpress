package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端通知客户端开始零一五第二枪计时器
 */
public record ZeroOneFiveSecondShotPayload(int shooterId) implements CustomPacketPayload {
    public static final Type<ZeroOneFiveSecondShotPayload> ID = new Type<>(SRE.id("zero_one_five_second_shot"));
    public static final StreamCodec<FriendlyByteBuf, ZeroOneFiveSecondShotPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            ZeroOneFiveSecondShotPayload::shooterId,
            ZeroOneFiveSecondShotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
