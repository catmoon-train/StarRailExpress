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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 会议投票出局时触发的事件（可拦截）。
 * 返回 false 可拦截出局（如政客免疫投票出局）。
 */
public interface MeetingVoteOutEvent {

    Event<MeetingVoteOutEvent> EVENT = EventFactory.createArrayBacked(MeetingVoteOutEvent.class,
            listeners -> (level, player) -> {
                for (MeetingVoteOutEvent listener : listeners) {
                    if (!listener.onVoteOut(level, player)) return false;
                }
                return true;
            });

    /**
     * @return false 阻止该玩家被投票出局
     */
    boolean onVoteOut(ServerLevel level, ServerPlayer player);
}
