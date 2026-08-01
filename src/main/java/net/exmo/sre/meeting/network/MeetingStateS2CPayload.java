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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

import static io.wifi.starrailexpress.SRE.MOD_ID;

/**
 * S2C：会议状态同步。会议开始 / 阶段切换 / 发言者变化 / 会议结束时全量下发。
 *
 * @param phase        0=无会议（结束），1=开场动画，2=讨论阶段
 * @param centerX/Y/Z  会议地点中心
 * @param phaseEndGameTime 当前阶段结束的世界 gameTime（客户端本地倒计时）
 * @param reporterName 发起会议的玩家名
 * @param victimName   被发现的尸体主人名（紧急按钮式会议为空串）
 * @param participants 参会者（存活者）UUID 列表
 * @param speakers     当前正在发言的玩家 UUID 列表（可多人）
 */
public record MeetingStateS2CPayload(int phase, double centerX, double centerY, double centerZ,
        long phaseEndGameTime, String reporterName, String victimName,
        List<UUID> participants, List<UUID> speakers) implements CustomPacketPayload {

    public static final Type<MeetingStateS2CPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "meeting_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeetingStateS2CPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.phase);
                buf.writeDouble(payload.centerX);
                buf.writeDouble(payload.centerY);
                buf.writeDouble(payload.centerZ);
                buf.writeLong(payload.phaseEndGameTime);
                buf.writeUtf(payload.reporterName);
                buf.writeUtf(payload.victimName);
                buf.writeVarInt(payload.participants.size());
                for (UUID uuid : payload.participants) {
                    buf.writeUUID(uuid);
                }
                buf.writeVarInt(payload.speakers.size());
                for (UUID uuid : payload.speakers) {
                    buf.writeUUID(uuid);
                }
            },
            buf -> {
                int phase = buf.readVarInt();
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                long end = buf.readLong();
                String reporter = buf.readUtf();
                String victim = buf.readUtf();
                int participantCount = buf.readVarInt();
                List<UUID> participants = new java.util.ArrayList<>(participantCount);
                for (int i = 0; i < participantCount; i++) {
                    participants.add(buf.readUUID());
                }
                int speakerCount = buf.readVarInt();
                List<UUID> speakers = new java.util.ArrayList<>(speakerCount);
                for (int i = 0; i < speakerCount; i++) {
                    speakers.add(buf.readUUID());
                }
                return new MeetingStateS2CPayload(phase, x, y, z, end, reporter, victim, participants, speakers);
            });

    @Override
    public Type<MeetingStateS2CPayload> type() {
        return ID;
    }
}
