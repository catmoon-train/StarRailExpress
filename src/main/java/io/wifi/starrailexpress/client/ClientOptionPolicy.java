package io.wifi.starrailexpress.client;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.ParticleStatus;

import java.util.Map;

/** 客户端公平性选项的唯一数值来源；不依赖客户端初始化先后或第三方 OptionLocker。 */
public final class ClientOptionPolicy {
    private static final Map<String, Object> LOCKED = Map.ofEntries(
            Map.entry("options.autoJump", false),
            Map.entry("options.gamma", 0.0D),
            Map.entry("options.darknessEffectScale", 1.0D),
            Map.entry("options.showSubtitles", false),
            Map.entry("options.renderClouds", CloudStatus.OFF),
            Map.entry("options.particles", ParticleStatus.ALL),
            Map.entry("options.ao", true),
            Map.entry("soundCategory.music", 0.0D),
            Map.entry("soundCategory.record", 0.1D),
            Map.entry("soundCategory.weather", 1.0D),
            Map.entry("soundCategory.block", 1.0D),
            Map.entry("soundCategory.hostile", 1.0D),
            Map.entry("soundCategory.neutral", 1.0D),
            Map.entry("soundCategory.player", 1.0D),
            Map.entry("soundCategory.ambient", 1.0D),
            Map.entry("soundCategory.voice", 1.0D));

    private ClientOptionPolicy() { }

    public static Object lockedValue(String key) {
        return LOCKED.get(key);
    }

    public static boolean isLocked(String key) {
        return LOCKED.containsKey(key);
    }
}
