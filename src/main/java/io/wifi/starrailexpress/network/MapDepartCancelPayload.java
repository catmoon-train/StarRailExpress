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

import io.netty.buffer.ByteBuf;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端判定本局无法“发车”（如人数不足）时发给客户端：
 * 让客户端退出地图投票结果页的铺黑显示，避免卡在黑屏。
 */
public class MapDepartCancelPayload implements CustomPacketPayload {
    public static final Type<MapDepartCancelPayload> TYPE = new Type<>(
            SRE.id("map_depart_cancel")
    );

    public static final StreamCodec<ByteBuf, MapDepartCancelPayload> CODEC = StreamCodec.of(
            (buf, packet) -> {}, buf -> new MapDepartCancelPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
