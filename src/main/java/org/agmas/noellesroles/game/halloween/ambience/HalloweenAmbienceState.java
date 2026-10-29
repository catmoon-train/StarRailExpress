package org.agmas.noellesroles.game.halloween.ambience;

/** 不依赖 Minecraft 的边沿状态机；世界按实例比较，避免换图后沿用旧音轨。 */
public final class HalloweenAmbienceState {
    public record Change(boolean stop, boolean start) {}
    private Object playingWorld;

    public static boolean enabled(boolean mapAndModeEnabled, boolean roundActive, boolean playerPresent) {
        return mapAndModeEnabled && roundActive && playerPresent;
    }

    public Change update(Object world, boolean enabled, boolean audible) {
        Object desired = enabled && audible ? world : null;
        boolean stop = playingWorld != null && playingWorld != desired;
        boolean start = desired != null && playingWorld != desired;
        playingWorld = desired;
        return new Change(stop, start);
    }

    /** 断线或音频引擎重载时丢弃旧实例；下一次有效 tick 才重新开始。 */
    public void reset() {
        playingWorld = null;
    }
}
