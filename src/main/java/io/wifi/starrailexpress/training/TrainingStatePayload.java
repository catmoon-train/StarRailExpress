package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.SRE;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 面板权威快照；仅首次打开包含目录，普通确认不重复发送目录。 */
public record TrainingStatePayload(CompoundTag state) implements CustomPacketPayload {
    public static final Type<TrainingStatePayload> ID = new Type<>(SRE.id("training_state"));
    public static final StreamCodec<FriendlyByteBuf, TrainingStatePayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeNbt(value.state),
            buf -> {
                var tag = buf.readNbt(net.minecraft.nbt.NbtAccounter.create(2_097_152L));
                if (tag != null && !(tag instanceof CompoundTag)) throw new io.netty.handler.codec.DecoderException("Training state must be a compound");
                return new TrainingStatePayload(tag instanceof CompoundTag state ? state : new CompoundTag());
            });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
