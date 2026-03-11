package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventType;

public record ReplayEvent(EventType eventType, long timestamp, EventDetails details) {
}