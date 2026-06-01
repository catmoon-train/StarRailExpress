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
