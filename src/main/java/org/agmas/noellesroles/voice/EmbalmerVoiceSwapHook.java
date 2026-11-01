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

package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerVoiceSwapManager;
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerVoiceSwapManager.Route;

/**
 * 嬉命人·语音互换的语音路由。
 *
 * <p>核心：不交换音频数据，只把「声源位置」换成对方。{@code channelId} 仍是说话者本人，
 * 所以语音 HUD 上显示的说话人不变；声音从交换对象所在位置发出，并随其移动
 * （主线程每一 tick 重新发布声源坐标，见 {@link EmbalmerVoiceSwapManager#route}）。
 *
 * <p>实现说明：转发 {@link LocationalSoundPacket}（定位包）而不是 {@code EntitySoundPacket}。
 * 项目依赖的 SVC {@code 1.21.1-2.6.18} 中，{@code EntitySoundPacketImpl$BuilderImpl#build()}
 * 虽然接受 {@code entityUuid}，但构造 {@code PlayerSoundPacket} 时只传 {@code channelId} 与
 * {@code sender}，实体 UUID 根本没有写进包体；客户端 {@code AudioChannel#writeToSpeaker}
 * 用 {@code PlayerSoundPacket#getSender()} 去世界里找发声实体，于是声音依旧从说话者自己身上发出
 * （上游修复 c42c35d 直到 2.6.25 才发布）。定位包走的是
 * {@code LocationSoundPacket#getLocation()} 分支，与本模组鹈鹕 / 闹事者 / 对讲机等转发路径一致。
 *
 * <p>线程约束：本类只读取 {@link EmbalmerVoiceSwapManager#route} 发布的不可变快照，
 * 不查询玩家列表、不读 CCA、不取实体坐标——这些都留在主线程的 {@code tick} 里完成。
 * 若在这里抛异常，SVC 的 {@code dispatchEvent} 会把它吞成一行 error 日志，
 * 随后默认广播照旧从说话者位置发出，表现为「技能完全没生效」。
 */
public final class EmbalmerVoiceSwapHook {

    private EmbalmerVoiceSwapHook() {
    }

    /**
     * @return true 表示本次麦克风包已被接管（已 {@code cancel()} 并手工转发），调用方应直接 return
     */
    public static boolean handle(MicrophonePacketEvent event, VoicechatServerApi api, ServerPlayer speaker) {
        if (event.isCancelled()) {
            return false;
        }
        Route route = EmbalmerVoiceSwapManager.route(speaker.getUUID());
        if (route == null || route.body().equals(speaker.getUUID())) {
            return false;
        }

        // 语音线程上任何异常都会被 SVC 的 dispatchEvent 吞成一行 error，且不取消默认广播。
        // 这里显式兜底：记一次带上下文的错误日志，然后放行默认广播，至少不会「悄无声息」。
        try {
            MicrophonePacket packet = event.getPacket();
            boolean whispering = packet.isWhispering();
            // 低语距离取服务端配置（与 Gexpress 一致）；没有配置时退回默认语音距离的一半
            float distance = (float) (whispering
                    ? api.getServerConfig().getDouble("whisper_distance", api.getVoiceChatDistance() / 2.0D)
                    : api.getVoiceChatDistance());

            // 以「交换对象」当前所在位置为声源，转发定位语音包；channelId 仍是说话者本人
            Position position = api.createPosition(route.origin().x, route.origin().y, route.origin().z);
            LocationalSoundPacket sound = packet.locationalSoundPacketBuilder()
                    .channelId(speaker.getUUID())
                    .position(position)
                    .distance(distance)
                    .build();

            // 维度同样来自主线程快照：语音线程上不调用 speaker.serverLevel()，
            // 避免跨线程访问服务端世界抛异常后被 SVC 吞成「毫无反应」。
            var level = api.fromServerLevel(route.level());
            double maxDistance = Math.max(distance, api.getBroadcastRange());
            for (de.maxhenkel.voicechat.api.ServerPlayer listener : api.getPlayersInRange(level, position,
                    maxDistance)) {
                if (listener.getUuid().equals(speaker.getUUID())) {
                    continue;
                }
                VoicechatConnection connection = api.getConnectionOf(listener.getUuid());
                // 与其它转发路径一致：只有已安装且已连接的客户端才能收包
                if (connection != null && connection.isInstalled() && connection.isConnected()) {
                    api.sendLocationalSoundPacketTo(connection, sound);
                }
            }
        } catch (RuntimeException e) {
            Noellesroles.LOGGER.error("[Embalmer] Failed to relay swapped voice for {} -> {}",
                    speaker.getName().getString(), route.body(), e);
            return false;
        }

        event.cancel();
        return true;
    }
}
