package io.wifi.starrailexpress.client;

import net.minecraft.client.CloudStatus;

import java.util.Map;

/** 客户端受限选项的唯一数值来源；声音、粒子和环境光遮蔽由用户自行调整。 */
public final class ClientOptionPolicy {
    private static final Map<String, Object> LOCKED = Map.ofEntries(
            Map.entry("options.autoJump", false),
            Map.entry("options.gamma", 0.0D),
            Map.entry("options.darknessEffectScale", 1.0D),
            Map.entry("options.showSubtitles", false),
            Map.entry("options.renderClouds", CloudStatus.OFF),
            Map.entry("options.entityDistanceScaling", 1.0D));

    private ClientOptionPolicy() { }

    public static Object lockedValue(String key) {
        return LOCKED.get(key);
    }

    public static boolean isLocked(String key) {
        return LOCKED.containsKey(key);
    }
}
