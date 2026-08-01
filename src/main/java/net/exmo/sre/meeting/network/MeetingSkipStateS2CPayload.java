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
 * S2C：会议跳过投票实时计票状态。
 *
 * @param skipCount   已投「跳过」的存活玩家数
 * @param aliveCount  当前场上存活玩家总数（阈值 = 超过 aliveCount/2）
 */
public record MeetingSkipStateS2CPayload(int skipCount, int aliveCount) implements CustomPacketPayload {

    public static final Type<MeetingSkipStateS2CPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_skip_state"));

    public static final StreamCodec<FriendlyByteBuf, MeetingSkipStateS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, MeetingSkipStateS2CPayload::skipCount,
            ByteBufCodecs.INT, MeetingSkipStateS2CPayload::aliveCount,
            MeetingSkipStateS2CPayload::new);

    @Override
    public Type<MeetingSkipStateS2CPayload> type() {
        return ID;
    }
}
