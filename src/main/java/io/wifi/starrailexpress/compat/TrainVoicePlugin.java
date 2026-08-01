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

package io.wifi.starrailexpress.compat;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import io.wifi.starrailexpress.SRE;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TrainVoicePlugin implements VoicechatPlugin {
    public static final UUID GROUP_ID = UUID.randomUUID();
    public static VoicechatServerApi SERVER_API;
    public static VoicechatClientApi CLIENT_API;
    
    public static Group GROUP;

    public static boolean isVoiceChatMissing() {
        return SERVER_API == null;
    }

    public static boolean isPlayerInGroup(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return false;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null) {
            if (GROUP == null)
                return false;
            if (GROUP != null) {
                return connection.isInGroup();
            }
        }
        return false;
    }

    public static void addPlayer(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null) {
            if (GROUP == null)
                GROUP = SERVER_API.groupBuilder().setHidden(true).setId(GROUP_ID).setName("Train Spectators")
                        .setPersistent(true).setType(Group.Type.OPEN).build();
            if (GROUP != null)
                connection.setGroup(GROUP);
        }
    }

    public static void resetPlayer(@NotNull UUID player) {
        if (isVoiceChatMissing())
            return;
        VoicechatConnection connection = SERVER_API.getConnectionOf(player);
        if (connection != null)
            connection.setGroup(null);
    }

    @Override
    public void registerEvents(@NotNull EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            SERVER_API = event.getVoicechat();
        });
        registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
            CLIENT_API = event.getVoicechat();
        });
        // 会议系统：讨论阶段未举手的参会者不发出语音——否则所有人都能抢话。
        // 本回调跑在 svc 的语音线程上，只允许读 MeetingManager 发布的不可变快照。
        registration.registerEvent(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender != null && sender.getPlayer() != null
                    && net.exmo.sre.meeting.MeetingManager.isVoiceMuted(sender.getPlayer().getUuid())) {
                event.cancel();
            }
        });
    }

    @Override
    public String getPluginId() {
        return SRE.MOD_ID;
    }
}