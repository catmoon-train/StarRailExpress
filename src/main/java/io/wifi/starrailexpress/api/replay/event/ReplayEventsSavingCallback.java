package io.wifi.starrailexpress.api.replay.event;

import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.List;

public interface ReplayEventsSavingCallback {
    Event<ReplayEventsSavingCallback> EVENT = EventFactory.createArrayBacked(ReplayEventsSavingCallback.class,
            listeners -> events -> {
                for (ReplayEventsSavingCallback listener : listeners) {
                    listener.onReplayEventsSaving(events);
                }
            });

    void onReplayEventsSaving(List<ReplayTimelineEvent> events);
}
