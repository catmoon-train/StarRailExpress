package io.wifi.srepokemoncompat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;

import java.util.HashSet;
import java.util.Set;

/**
 * 可选兼容在 Mixin 准备期的可用性判断。
 *
 * <p>精简壳（registry-only shell）保留模组 ID 与注册项，但故意不含原玩法类；
 * 这时兼容混入既无目标也无意义，应当直接跳过而不是让服务器启动失败。</p>
 */
public final class CompatTargets {
    private static final Set<String> WARNED = new HashSet<>();

    private CompatTargets() {
    }

    /** 目标模组是否为精简注册壳（依据其 fabric.mod.json 的 train:variant 标记）。 */
    public static boolean isRegistryShell(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) return false;
        CustomValue value = container.get().getMetadata().getCustomValue("train:variant");
        return value != null && value.getType() == CustomValue.CvType.STRING
                && "registry-only-shell".equals(value.getAsString());
    }

    /** 目标类是否真的存在于类路径中；只查字节资源，不提前加载目标类。 */
    public static boolean hasTargetClass(String target) {
        return CompatTargets.class.getClassLoader().getResource(target.replace('.', '/') + ".class") != null;
    }

    /** 每个缺失目标只告警一次，避免刷屏又保留诊断信息。 */
    public static boolean warnMissingOnce(String modId, String target) {
        String key = modId + ':' + target;
        synchronized (WARNED) {
            if (!WARNED.add(key)) return false;
        }
        System.getLogger("sre_pokemon_compat").log(System.Logger.Level.WARNING,
                "Skipped optional " + modId + " compatibility mixin: target class " + target
                        + " is not present (registry-only shell or different mod build).");
        return true;
    }
}
