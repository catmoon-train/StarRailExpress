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

/**
 * 展示方块（文本展示 / 方块展示）的网络包定义。
 *
 * <p>只需要两个包：服务端发 {@link OpenUI} 打开编辑界面，客户端发 {@link Save} 回存。
 * 保存后的数据同步直接复用原版方块实体更新包（{@code level.sendBlockUpdated}），
 * 所以不需要额外的自定义同步包。
 */
public class DisplayBlockPayload {

    /** 服务端 → 客户端：打开编辑界面，并带上当前展示数据。 */
    public record OpenUI(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenUI> TYPE = new Type<>(SRE.id("display_block_open_ui"));
        public static final StreamCodec<FriendlyByteBuf, OpenUI> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenUI::pos,
                ByteBufCodecs.COMPOUND_TAG, OpenUI::data,
                OpenUI::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：保存展示数据。 */
    public record Save(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Save> TYPE = new Type<>(SRE.id("display_block_save"));
        public static final StreamCodec<FriendlyByteBuf, Save> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Save::pos,
                ByteBufCodecs.COMPOUND_TAG, Save::data,
                Save::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
