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

package io.wifi.starrailexpress.network.packet;

import io.netty.buffer.ByteBuf;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端上报「志愿海选界面已经打开」（无负载，玩家身份取自连接）。
 *
 * <p>
 * 服务端用它来决定何时开始一阶段的计时：开局发车黑幕 / 动画播完、界面真正可见之后
 * 才开始倒计时，避免玩家在看动画时白白消耗选职业的时间。
 */
public record VolunteerOpenReadyC2SPacket() implements CustomPacketPayload {

    public static final Type<VolunteerOpenReadyC2SPacket> TYPE = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "volunteer_open_ready"));

    public static final StreamCodec<ByteBuf, VolunteerOpenReadyC2SPacket> CODEC =
            StreamCodec.of((buf, packet) -> {
            }, buf -> new VolunteerOpenReadyC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
