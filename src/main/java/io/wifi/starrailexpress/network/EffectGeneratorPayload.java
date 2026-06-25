package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class EffectGeneratorPayload {
    public record OpenConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenConfig> TYPE = new Type<>(SRE.id("effect_generator_open_config"));
        public static final StreamCodec<FriendlyByteBuf, OpenConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenConfig::data,
                OpenConfig::new);

        @Override
        public Type<OpenConfig> type() {
            return TYPE;
        }
    }

    public record SaveConfig(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<SaveConfig> TYPE = new Type<>(SRE.id("effect_generator_save_config"));
        public static final StreamCodec<FriendlyByteBuf, SaveConfig> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SaveConfig::pos,
                ByteBufCodecs.COMPOUND_TAG, SaveConfig::data,
                SaveConfig::new);

        @Override
        public Type<SaveConfig> type() {
            return TYPE;
        }
    }
}
