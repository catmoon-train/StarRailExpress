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

package net.exmo.sre.meeting.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * C2S：瞄准尸体按「上报会议」键，请求以该尸体召开紧急会议。
 *
 * @param bodyEntityId 准星指向的尸体（PlayerBodyEntity）实体 ID，服务端重新校验类型与距离
 */
public record MeetingReportC2SPayload(int bodyEntityId) implements CustomPacketPayload {

    public static final Type<MeetingReportC2SPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_report"));

    public static final StreamCodec<FriendlyByteBuf, MeetingReportC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeetingReportC2SPayload::bodyEntityId,
            MeetingReportC2SPayload::new);

    @Override
    public Type<MeetingReportC2SPayload> type() {
        return ID;
    }
}
