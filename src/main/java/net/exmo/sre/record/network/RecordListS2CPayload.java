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

package net.exmo.sre.record.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：返回战绩列表的某一页。
 * {@code offset} 为该页首条在整体中的下标，{@code total} 为数据库内总条数（用于虚拟列表滚动条），
 * {@code json} 为该页摘要的 JSON 数组。
 */
public record RecordListS2CPayload(int offset, int total, String json) implements CustomPacketPayload {
    public static final Type<RecordListS2CPayload> ID = new Type<>(SRE.id("record_list"));
    public static final StreamCodec<FriendlyByteBuf, RecordListS2CPayload> CODEC =
            CustomPacketPayload.codec(RecordListS2CPayload::write, RecordListS2CPayload::new);

    private RecordListS2CPayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(offset);
        buffer.writeVarInt(total);
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
