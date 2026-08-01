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

package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.api.replay.event.ReplayEventRecordedCallback;

public final class ReplayRecorder {
    private final ReplaySession session;

    public ReplayRecorder(ReplaySession session) {
        this.session = session;
    }

    public ReplayTimelineEvent record(ReplayTimelineEvent event) {
        session.addTimelineEvent(event);
        ReplayEventRecordedCallback.EVENT.invoker().onReplayEventRecorded(event, session.timelineSnapshot());
        return event;
    }
}
