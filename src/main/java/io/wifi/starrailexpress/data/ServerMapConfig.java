package io.wifi.starrailexpress.data;

import com.google.gson.Gson;

import io.wifi.starrailexpress.data.MapConfig.MapEntry;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ServerMapConfig {
    private static final Gson gson = new Gson();
    private static ServerMapConfig instance;

    private List<MapConfig.MapEntry> maps;
    // private final Path configPath = Paths.get("world", "tmm_maps.json");

    public static synchronized ServerMapConfig getInstance(ServerLevel sl) {
        return getInstance(sl.getServer());
    }

    public static synchronized ServerMapConfig getInstance(MinecraftServer sl) {
        if (instance == null) {
            instance = loadOrCreateConfig(sl);
        }
        return instance;
    }

    public static synchronized void reload(MinecraftServer sl) {
        instance = loadOrCreateConfig(sl);
    }

    public static synchronized void reload(ServerLevel sl) {
        reload(sl.getServer());
    }

    public List<MapEntry> getRandomMaps() {
        return getRandomMaps(SREConfig.instance().mapRandomCount);
    }
    public static List<MapEntry> cache_maps = new ArrayList<>();

    public List<MapEntry> getRandomMaps(int count) {
        if (count < 0) {
            return this.maps;
        }
        var a = new ArrayList<>(this.maps);
        a.removeIf(
                mapEntry -> !mapEntry.canSelect
        );
        Collections.shuffle(a);
        List<MapEntry> mapEntries = a.subList(0, count);
        cache_maps = mapEntries;
        return mapEntries;
    }

    private static ServerMapConfig loadOrCreateConfig(MinecraftServer sl) {
        ServerMapConfig config = new ServerMapConfig();
        Path configPath = Paths.get(sl.getWorldPath(LevelResource.ROOT).toString(), "train_vote_maps.json");
        // 尝试从服务器配置目录加载配置
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                MapConfig loadedConfig = gson.fromJson(reader, MapConfig.class);
                if (loadedConfig != null && loadedConfig.getMaps() != null) {
                    config.maps = loadedConfig.getMaps();
                    return config;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 如果配置文件不存在或加载失败，使用默认配置并保存
        MapConfig defaultConfig = MapConfig.createDefaultConfig();
        config.maps = defaultConfig.getMaps();
        config.saveConfig(sl);
        return config;
    }

    public void saveConfig(MinecraftServer sl) {
        try {
            // 确保配置目录存在
            Path configPath = Paths.get(sl.getWorldPath(LevelResource.ROOT).toString(),
                    "train_vote_maps.json");

            Files.createDirectories(configPath.getParent());

            MapConfig tempConfig = new MapConfig();
            tempConfig.maps = this.maps;

            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(tempConfig, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<MapConfig.MapEntry> getMaps() {
        return maps;
    }

    public MapConfig.MapEntry getMapById(String id) {
        if (maps != null) {
            for (MapConfig.MapEntry entry : maps) {
                if (entry.getId().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }
}