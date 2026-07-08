package io.wifi.starrailexpress.game.data;

public enum MapStatusBarType {
    NONE,
    WARMTH,
    THIRST,
    HUNGER,
    POLLUTION;

    public static MapStatusBarType byName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        for (MapStatusBarType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return NONE;
    }
}
