package io.wifi.starrailexpress.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WaypointManager extends SavedData {
    private static final String DATA_NAME = "starrailexpress_waypoints";
    private final Map<String, List<WaypointData>> waypoints = new ConcurrentHashMap<>();
    private final Path serverDataDir;

    public WaypointManager(Path serverDataDir) {
        this.serverDataDir = serverDataDir;
    }

    public WaypointManager(Map<String, List<WaypointData>> waypoints, Path serverDataDir) {
        this.waypoints.putAll(waypoints);
        this.serverDataDir = serverDataDir;
    }

    public static WaypointManager get(ServerLevel level) {
        return get(level.getServer());
    }

    public static WaypointManager get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD); // 使用主世界存储
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
            new SavedData.Factory<WaypointManager>(() -> new WaypointManager(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)),
                    (a,b)->load(a),
                                  null),
            DATA_NAME
        );
    }

    public static WaypointManager load(CompoundTag tag) {
        // 这里使用旧的NBT加载方式，但现在我们使用JSON，所以暂时返回空实例
        return new WaypointManager(Paths.get("."));
    }


    public void saveToFile() {
        try {
            Path waypointFile = serverDataDir.resolve("waypoints.json");
            
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create(); // 移除了excludeFieldsWithoutExposeAnnotation()，因为我们使用了getter/setter

            String json = gson.toJson(waypoints);
            
            Files.write(waypointFile, json.getBytes(StandardCharsets.UTF_8));
            setDirty(true); // 标记为脏数据以便保存
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadFromFile() {
        try {
            Path waypointFile = serverDataDir.resolve("waypoints.json");
            
            if (!Files.exists(waypointFile)) {
                // 如果文件不存在，初始化为空集合
                return;
            }

            String json = Files.readString(waypointFile, StandardCharsets.UTF_8);
            
            Gson gson = new Gson();
            TypeToken<Map<String, List<WaypointData>>> token = new TypeToken<Map<String, List<WaypointData>>>() {};
            Map<String, List<WaypointData>> loadedWaypoints = gson.fromJson(json, token.getType());

            if (loadedWaypoints != null) {
                waypoints.clear();
                waypoints.putAll(loadedWaypoints);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addWaypoint(String path, String name, BlockPos pos, int color) {
        waypoints.computeIfAbsent(path, k -> new ArrayList<>()).add(new WaypointData(path, name, pos, color));
        saveToFile();
    }

    public void removeWaypoint(String path, String name) {
        List<WaypointData> pathWaypoints = waypoints.get(path);
        if (pathWaypoints != null) {
            pathWaypoints.removeIf(wp -> wp.getName().equals(name));
            if (pathWaypoints.isEmpty()) {
                waypoints.remove(path);
            }
            saveToFile();
        }
    }

    public void removePath(String path) {
        waypoints.remove(path);
        saveToFile();
    }

    public List<WaypointData> getWaypointsByPath(String path) {
        return waypoints.getOrDefault(path, new ArrayList<>());
    }

    public List<WaypointData> getAllWaypoints() {
        return waypoints.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    public Set<String> getAllPaths() {
        return new HashSet<>(waypoints.keySet());
    }

    public Map<String, List<WaypointData>> getAllWaypointsMap() {
        return new HashMap<>(waypoints);
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        return compoundTag;
    }
}