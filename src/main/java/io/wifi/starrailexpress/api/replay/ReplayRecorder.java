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
