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

/** C2S：会议中开始 / 结束发言（按键或 GUI 触发）。 */
public record MeetingSpeakC2SPayload(boolean speaking) implements CustomPacketPayload {

    public static final Type<MeetingSpeakC2SPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_speak"));

    public static final StreamCodec<FriendlyByteBuf, MeetingSpeakC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MeetingSpeakC2SPayload::speaking,
            MeetingSpeakC2SPayload::new);

    @Override
    public Type<MeetingSpeakC2SPayload> type() {
        return ID;
    }
}
