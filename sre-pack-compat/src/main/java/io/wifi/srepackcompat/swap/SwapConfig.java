package io.wifi.srepackcompat.swap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 保留原模组的文件名和字段，使已有配置可直接沿用。 */
public final class SwapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean modEnabled = true;
    public int holdThresholdMs = 200;
    public boolean soundEnabled = true;
    public boolean mouseReposition = true;
    public boolean guiSwapEnabled = false;
    public boolean emptySlotSwapEnabled = false;
    public boolean debug = false;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("susinstantswap.json");
    }

    public static SwapConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                SwapConfig cfg = GSON.fromJson(reader, SwapConfig.class);
                if (cfg != null) {
                    cfg.clamp();
                    return cfg;
                }
            } catch (IOException | RuntimeException e) {
                LoggerFactory.getLogger(SwapConfig.class).warn("无法读取探囊取物配置，使用默认值且保留原文件", e);
            }
            return new SwapConfig();
        }
        SwapConfig cfg = new SwapConfig();
        cfg.save();
        return cfg;
    }

    public void clamp() { holdThresholdMs = Math.clamp(holdThresholdMs, 50, 1000); }

    public void save() {
        clamp();
        try {
            Files.createDirectories(path().getParent());
            try (var writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(SwapConfig.class).warn("无法保存探囊取物配置", e);
        }
    }
}
