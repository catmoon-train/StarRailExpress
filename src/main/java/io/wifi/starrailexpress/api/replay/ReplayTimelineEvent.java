package io.wifi.starrailexpress.api.replay;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public record ReplayTimelineEvent(
        UUID id,
        ReplayEventTypes.EventType type,
        long timestamp,
        long relativeTimestamp,
        @Nullable ReplayPlayerProfile actor,
        @Nullable ReplayPlayerProfile target,
        Component text,
        boolean hidden,
        Map<String, String> data) {
}
