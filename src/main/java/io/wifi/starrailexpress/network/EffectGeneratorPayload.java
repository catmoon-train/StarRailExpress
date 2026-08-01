/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
