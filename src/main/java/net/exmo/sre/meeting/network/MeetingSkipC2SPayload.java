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

/** C2S：投票跳过会议（GUI 按钮点击，可再次点击取消）。 */
public record MeetingSkipC2SPayload(boolean skip) implements CustomPacketPayload {

    public static final Type<MeetingSkipC2SPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_skip"));

    public static final StreamCodec<FriendlyByteBuf, MeetingSkipC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MeetingSkipC2SPayload::skip,
            MeetingSkipC2SPayload::new);

    @Override
    public Type<MeetingSkipC2SPayload> type() {
        return ID;
    }
}
