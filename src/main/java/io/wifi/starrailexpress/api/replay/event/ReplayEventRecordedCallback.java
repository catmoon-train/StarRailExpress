package io.wifi.starrailexpress.api.replay.event;

import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.List;

public interface ReplayEventRecordedCallback {
    Event<ReplayEventRecordedCallback> EVENT = EventFactory.createArrayBacked(ReplayEventRecordedCallback.class,
            listeners -> (event, snapshot) -> {
                for (ReplayEventRecordedCallback listener : listeners) {
                    listener.onReplayEventRecorded(event, snapshot);
                }
            });

    void onReplayEventRecorded(ReplayTimelineEvent event, List<ReplayTimelineEvent> snapshot);
}
