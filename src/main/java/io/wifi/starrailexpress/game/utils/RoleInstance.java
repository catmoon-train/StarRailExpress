package io.wifi.starrailexpress.game.utils;

import io.wifi.starrailexpress.api.SRERole;

import java.util.UUID;

public record RoleInstance(UUID uuid, SRERole role) {
    @Override
    public String toString() {
        return uuid.toString() + "@" + role.toString();
    }
}
