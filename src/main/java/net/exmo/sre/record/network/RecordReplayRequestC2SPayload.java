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
 * 客户端 -> 服务端：请求某一场战绩的完整回放数据。
 */
public record RecordReplayRequestC2SPayload(String matchId) implements CustomPacketPayload {
    public static final Type<RecordReplayRequestC2SPayload> ID = new Type<>(SRE.id("record_replay_request"));
    public static final StreamCodec<FriendlyByteBuf, RecordReplayRequestC2SPayload> CODEC =
            CustomPacketPayload.codec(RecordReplayRequestC2SPayload::write, RecordReplayRequestC2SPayload::new);

    private RecordReplayRequestC2SPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(64));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(matchId, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
