package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.SRE;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 操作仅携带目录键、槽位与有界设置，物品组件由服务端构造。 */
public record TrainingActionPayload(String action, CompoundTag data) implements CustomPacketPayload {
    public static final Type<TrainingActionPayload> ID = new Type<>(SRE.id("training_action"));
    public static final StreamCodec<FriendlyByteBuf, TrainingActionPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.action, 32); buf.writeNbt(value.data); },
            buf -> {
                String action = buf.readUtf(32);
                var tag = buf.readNbt(net.minecraft.nbt.NbtAccounter.create(16_384L));
                if (tag != null && !(tag instanceof CompoundTag)) throw new io.netty.handler.codec.DecoderException("Training action must be a compound");
                return new TrainingActionPayload(action, tag instanceof CompoundTag data ? data : new CompoundTag());
            });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
