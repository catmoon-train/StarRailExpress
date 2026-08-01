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
 * 客户端 -> 服务端：按需请求战绩列表中的一页（{@code offset} 起 {@code limit} 条）。
 * 仅在滚动到对应区间时发送，以减少流量。
 */
public record RecordListRequestC2SPayload(int offset, int limit) implements CustomPacketPayload {
    public static final Type<RecordListRequestC2SPayload> ID = new Type<>(SRE.id("record_list_request"));
    public static final StreamCodec<FriendlyByteBuf, RecordListRequestC2SPayload> CODEC =
            CustomPacketPayload.codec(RecordListRequestC2SPayload::write, RecordListRequestC2SPayload::new);

    private RecordListRequestC2SPayload(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(offset);
        buffer.writeVarInt(limit);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
