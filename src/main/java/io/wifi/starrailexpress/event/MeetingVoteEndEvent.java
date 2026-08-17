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

import net.fabricmc.fabric.api.event.EventFactory;
import io.wifi.starrailexpress.content.vote.VoteSession;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;

public interface MeetingVoteEndEvent {

    Event<MeetingVoteEndEvent> EVENT = EventFactory.createArrayBacked(MeetingVoteEndEvent.class,
            listeners -> (level, session) -> {
                for (MeetingVoteEndEvent listener : listeners) {
                    listener.onVoteOver(level, session);
                }
            });

    /**
     * @return false 阻止该玩家被投票出局
     */
    void onVoteOver(ServerLevel level, VoteSession session);
}
