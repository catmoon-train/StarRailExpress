package io.wifi.srepackcompat.config;

import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/** Chloride 1.8.1 的整包策略，数值仅来自兼容 JAR 内置 TOML。 */
public final class ChlorideOptionPolicy {
    private static ChlorideConfigSnapshot snapshot;

    private ChlorideOptionPolicy() { }

    public static boolean isLocked(ResourceLocation id) {
        return id != null && id.getNamespace().equals("chloride") && !id.getPath().equals("full_screen");
    }

    public static synchronized void enforce() {
        if (snapshot == null) snapshot = loadSnapshot();
        snapshot.restore();
    }

    @SuppressWarnings("unchecked")
    private static ChlorideConfigSnapshot loadSnapshot() {
        try {
            Path path = FabricLoader.getInstance().getModContainer("sre_pack_compat").orElseThrow()
                    .findPath("config/chloride-client.toml").orElseThrow();
            ClassLoader loader = ChlorideOptionPolicy.class.getClassLoader();
            Class<?> formatClass = Class.forName("me.srrapero720.waterconfig.impl.formats.TOMLFormat", true, loader);
            Object format = formatClass.getConstructor().newInstance();
            Class<?> readerApi = Class.forName("me.srrapero720.waterconfig.api.formats.IFormatReader", true, loader);
            // 正式包资源是 ZipPath，WaterConfig 的 Path.toFile() 只能读取普通文件。
            try (var nativeFile = NativeConfigFile.copyOf(path);
                 var reader = (AutoCloseable) formatClass.getMethod("createReader", Path.class).invoke(format, nativeFile.path())) {
                Map<String, Object> entries = (Map<String, Object>) readerApi.getMethod("entries").invoke(reader);
                entries = new java.util.HashMap<>(entries);
                entries.remove("fullscreen.mode"); // 窗口模式由用户选择，不在下一 tick 恢复。
                Class<?> config = Class.forName("me.srrapero720.chloride.ChlorideConfig", true, loader);
                var result = new ChlorideConfigSnapshot(config, entries, ResourceLocation::parse);
                LoggerFactory.getLogger(ChlorideOptionPolicy.class).info("Chloride 已锁定 {} 项内置设置", result.size());
                return result;
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法读取内置 Chloride 锁定配置，请核对兼容包与 Chloride 版本", e);
        }
    }

    public static <T> OptionBinding<T> binding(ResourceLocation id, OptionBinding<T> original) {
        if (!isLocked(id)) return original;
        enforce();
        T fixed = original.load();
        return new OptionBinding<>() {
            @Override public void save(T ignored) { enforce(); }
            @Override public T load() { enforce(); return fixed; }
        };
    }
}
