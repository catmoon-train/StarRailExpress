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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 志愿海选模式的选择请求。
 *
 * @param volunteer true 表示一阶段（提交志愿职业，用 roleId）；false 表示二阶段（挑选海选池下标，用 index）
 * @param index     海选池下标
 * @param roleId    志愿职业 id
 */
public record VolunteerOpenSelectC2SPacket(boolean volunteer, int index, String roleId)
        implements CustomPacketPayload {

    public static final Type<VolunteerOpenSelectC2SPacket> TYPE = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "volunteer_open_select"));

    public static final StreamCodec<ByteBuf, VolunteerOpenSelectC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            VolunteerOpenSelectC2SPacket::volunteer,
            ByteBufCodecs.VAR_INT,
            VolunteerOpenSelectC2SPacket::index,
            ByteBufCodecs.STRING_UTF8,
            VolunteerOpenSelectC2SPacket::roleId,
            VolunteerOpenSelectC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
