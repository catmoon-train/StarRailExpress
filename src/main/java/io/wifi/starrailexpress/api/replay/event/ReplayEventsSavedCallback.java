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

package io.wifi.starrailexpress.api.replay.event;

import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.nio.file.Path;
import java.util.List;

public interface ReplayEventsSavedCallback {
    Event<ReplayEventsSavedCallback> EVENT = EventFactory.createArrayBacked(ReplayEventsSavedCallback.class,
            listeners -> (path, events) -> {
                for (ReplayEventsSavedCallback listener : listeners) {
                    listener.onReplayEventsSaved(path, events);
                }
            });

    void onReplayEventsSaved(Path path, List<ReplayTimelineEvent> events);
}
