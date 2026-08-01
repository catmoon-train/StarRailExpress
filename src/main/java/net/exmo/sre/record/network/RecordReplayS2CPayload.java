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
 * 服务端 -> 客户端：返回某一场战绩的完整回放数据（{@link net.exmo.sre.record.MatchRecord} 的 JSON）。
 * {@code json} 为空表示该战绩不存在或读取失败。客户端收到后会打开回放查看 GUI。
 */
public record RecordReplayS2CPayload(String matchId, String json) implements CustomPacketPayload {
    public static final Type<RecordReplayS2CPayload> ID = new Type<>(SRE.id("record_replay"));
    public static final StreamCodec<FriendlyByteBuf, RecordReplayS2CPayload> CODEC =
            CustomPacketPayload.codec(RecordReplayS2CPayload::write, RecordReplayS2CPayload::new);

    private RecordReplayS2CPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(64), buffer.readUtf(1_048_576));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(matchId, 64);
        buffer.writeUtf(json, 1_048_576);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
