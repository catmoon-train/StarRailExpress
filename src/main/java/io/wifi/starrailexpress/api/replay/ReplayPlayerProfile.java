package io.wifi.starrailexpress.api.replay;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ReplayPlayerProfile(
        @Nullable UUID uuid,
        String name,
        @Nullable String roleId,
        Component roleName,
        boolean alive) {
    public static ReplayPlayerProfile unknown() {
        return new ReplayPlayerProfile(null, "Unknown", null,
                Component.translatable("sre.replay.event.unknown_player"), false);
    }
}
