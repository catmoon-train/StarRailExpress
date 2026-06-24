package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.game.data.MapStatusBarType;

public final class MapStatusBarClientState {
    private static MapStatusBarType type = MapStatusBarType.NONE;
    private static int value = 20;
    private static int maxValue = 20;

    private MapStatusBarClientState() {
    }

    public static void set(MapStatusBarType newType, int newValue, int newMaxValue) {
        type = newType == null ? MapStatusBarType.NONE : newType;
        value = Math.max(0, newValue);
        maxValue = Math.max(1, newMaxValue);
    }

    public static MapStatusBarType type() {
        return type;
    }

    public static int value() {
        return value;
    }

    public static int maxValue() {
        return maxValue;
    }
}
