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
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerVoiceSwapManager;

/**
 * 嬉命人·语音互换的语音路由。
 *
 * <p>核心：不交换音频数据，只把「声源位置」换成对方。{@code channelId} 仍是说话者本人，
 * 所以语音 HUD 上显示的说话人不变；声音从交换对象所在位置发出，并随其移动
 * （每个麦克风包都会按对方当前坐标重新转发）。
 *
 * <p>实现说明：转发 {@link LocationalSoundPacket}（定位包）而不是 {@code EntitySoundPacket}。
 * 项目依赖的 SVC {@code 1.21.1-2.6.18} 中，{@code EntitySoundPacket.Builder#entityUuid} 在
 * {@code build()} 里会被忽略——只参与空校验，实际仍沿用 {@code entitySoundPacketBuilder()}
 * 预填的 {@code sender}（即说话者本人）；而客户端正是按 {@code sender} 查找发声实体的，
 * 于是声音依旧从说话者自己位置发出（上游修复提交 c42c35d 直到 2.6.25 才发布）。
 * 定位包与本模组其他语音转发（鹈鹕 / 闹事者 / 对讲机等）走同一条成熟路径，各版本行为一致。
 *
 * <p>其他要点：
 * <ul>
 * <li>低语距离读服务端配置 {@code whisper_distance}，缺省退回语音距离的一半；</li>
 * <li>用 {@code getPlayersInRange} 以「交换对象所在位置」为圆心取收听范围内的玩家，
 * 逐个 {@code sendLocationalSoundPacketTo}；</li>
 * <li>转发完成后 {@code cancel()} 原麦克风包，阻止默认从说话者位置广播。</li>
 * </ul>
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
        ServerPlayer body = EmbalmerVoiceSwapManager.voiceBody(speaker);
        if (body == null || body.getUUID().equals(speaker.getUUID())) {
            return false;
        }

        MicrophonePacket packet = event.getPacket();
        boolean whispering = packet.isWhispering();
        // 低语距离取服务端配置（与 Gexpress 一致）；没有配置时退回默认语音距离的一半
        float distance = (float) (whispering
                ? api.getServerConfig().getDouble("whisper_distance", api.getVoiceChatDistance() / 2.0D)
                : api.getVoiceChatDistance());

        // 以「交换对象」当前所在位置为声源，转发定位语音包。
        // 不能改用 entitySoundPacketBuilder() + entityUuid()：SVC 2.6.18 的
        // EntitySoundPacket.Builder#build() 会忽略 entityUuid（只做空校验），
        // 实际仍沿用 entitySoundPacketBuilder() 预填的 sender（= 说话者本人），
        // 客户端按 sender 解析发声实体，声音依旧从说话者自己位置发出
        // （上游修复 c42c35d 直到 2.6.25 才发布）。定位包稳定且随包更新坐标。
        // channelId 仍是说话者本人：语音 HUD 上显示的说话人不变。
        LocationalSoundPacket.Builder<?> builder = packet.locationalSoundPacketBuilder();
        builder.channelId(speaker.getUUID())
                .position(api.createPosition(body.getX(), body.getY(), body.getZ()))
                .distance(distance);
        LocationalSoundPacket sound = builder.build();

        // 以「声源位置（交换对象）」为圆心，向语音可达范围内的玩家逐个转发
        Position position = api.createPosition(body.getX(), body.getY(), body.getZ());
        double maxDistance = Math.max(distance, api.getBroadcastRange());
        for (de.maxhenkel.voicechat.api.ServerPlayer listener : api.getPlayersInRange(
                api.fromServerLevel(speaker.serverLevel()), position, maxDistance)) {
            if (listener.getUuid().equals(speaker.getUUID())) {
                continue;
            }
            VoicechatConnection connection = api.getConnectionOf(listener.getUuid());
            if (connection != null) {
                api.sendLocationalSoundPacketTo(connection, sound);
            }
        }

        event.cancel();
        return true;
    }
}
