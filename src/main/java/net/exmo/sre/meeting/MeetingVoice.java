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

package net.exmo.sre.meeting;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.UUID;

/**
 * 会议语音（Simple Voice Chat 集成）：会议期间把所有参会者拉进同一个临时语音组，
 * 保证无论座位间距离多远都能互相听见；会议结束后退出。
 * 未安装 svc 时全部静默跳过。
 */
final class MeetingVoice {

    private static final UUID GROUP_ID = UUID.fromString("7f1c3a52-8f4e-4b7a-9c3d-2e5a6b8d9f01");
    private static Group group;

    private MeetingVoice() {
    }

    static void joinAll(Set<UUID> players, MinecraftServer server) {
        if (TrainVoicePlugin.isVoiceChatMissing()) {
            return;
        }
        if (group == null) {
            group = TrainVoicePlugin.SERVER_API.groupBuilder()
                    .setHidden(true)
                    .setId(GROUP_ID)
                    .setName("Emergency Meeting")
                    .setPersistent(true)
                    .setType(Group.Type.OPEN)
                    .build();
        }
        if (group == null) {
            return;
        }
        for (UUID uuid : players) {
            VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(uuid);
            if (connection != null && !connection.isInGroup()) {
                connection.setGroup(group);
            }
        }
    }

    static void leaveAll(Set<UUID> players, MinecraftServer server) {
        if (TrainVoicePlugin.isVoiceChatMissing() || group == null) {
            return;
        }
        for (UUID uuid : players) {
            VoicechatConnection connection = TrainVoicePlugin.SERVER_API.getConnectionOf(uuid);
            if (connection != null && connection.isInGroup()
                    && connection.getGroup() != null && GROUP_ID.equals(connection.getGroup().getId())) {
                connection.setGroup(null);
            }
        }
    }
}
