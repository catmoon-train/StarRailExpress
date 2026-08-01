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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端通知客户端开始零一五第二枪计时器
 */
public record ZeroOneFiveSecondShotPayload(int shooterId) implements CustomPacketPayload {
    public static final Type<ZeroOneFiveSecondShotPayload> ID = new Type<>(SRE.id("zero_one_five_second_shot"));
    public static final StreamCodec<FriendlyByteBuf, ZeroOneFiveSecondShotPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            ZeroOneFiveSecondShotPayload::shooterId,
            ZeroOneFiveSecondShotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
