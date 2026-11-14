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

package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * 会议参会者过滤：在存活玩家名单被收进会议之前，让拥有「进行中专属场景」的职业把自己
 * 的参与者排除在本次会议之外。被排除的玩家不传送、不就座、不投票，也不进入参会名单，
 * 由该职业自行保存并在散会后回到原处。
 *
 * <p>该事件只在开会时询问一次；任何监听器返回 {@code false} 即排除该玩家。
 * 未注册监听器时所有存活玩家照常参会。
 */
public interface MeetingParticipantEvent {

    Event<MeetingParticipantEvent> ALLOW_PARTICIPANT = EventFactory.createArrayBacked(
            MeetingParticipantEvent.class,
            listeners -> player -> {
                for (MeetingParticipantEvent listener : listeners) {
                    if (!listener.allowParticipant(player)) {
                        return false;
                    }
                }
                return true;
            });

    /**
     * 该玩家是否参与本次会议。
     *
     * @param player 存活玩家
     * @return {@code false} 表示排除在本次会议之外
     */
    boolean allowParticipant(ServerPlayer player);
}
