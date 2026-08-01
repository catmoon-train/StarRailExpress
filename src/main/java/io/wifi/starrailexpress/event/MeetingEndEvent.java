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

/**
 * 会议结束时触发的事件（非拦截型）。
 *
 * <p>Event interface fired when an emergency meeting ends (non-cancellable).
 */
public interface MeetingEndEvent {

    Event<MeetingEndEvent> EVENT = EventFactory.createArrayBacked(MeetingEndEvent.class,
            listeners -> (serverLevel) -> {
                for (MeetingEndEvent listener : listeners) {
                    listener.onMeetingEnd(serverLevel);
                }
            });

    /**
     * 会议结束时的回调。
     *
     * @param serverLevel 会议所在的服务端世界
     */
    void onMeetingEnd(ServerLevel serverLevel);
}
