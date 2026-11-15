package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.SRE;
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
        return lockedValue(key, "options.gamma".equals(key) && SRE.isLobby);
    }

    /** 显式状态版本便于验证往返切换；非大厅沿用原来的 0% 亮度。 */
    public static Object lockedValue(String key, boolean lobby) {
        if ("options.gamma".equals(key)) return lobby ? 1.0D : 0.0D;
        return LOCKED.get(key);
    }

    public static boolean isLocked(String key) {
        return LOCKED.containsKey(key);
    }
}
