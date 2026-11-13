/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WaypointManager extends SavedData {
    private static final String DATA_NAME = "starrailexpress_waypoints";
    /**
     * 冒险家技能创建的「本局临时标点」专用路径。
     *
     * <p>与 OP 用 {@code /tmm:createpoint} 建的永久地图标点分开：不写入
     * {@code waypoints.json}，只存在于内存并随全量同步下发，由技能计时器、
     * 开局 / 局末 / 服务器启动统一清除。客户端也用这个 path 识别并优先显示它们。</p>
     */
    public static final String ADVENTURER_PATH = "_adventurer";
    private final Map<String, List<WaypointData>> waypoints = new ConcurrentHashMap<>();
    private Path serverDataDir;

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
        WaypointManager manager = storage.computeIfAbsent(
            new SavedData.Factory<WaypointManager>(() -> new WaypointManager(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)),
                    (a,b)->load(a),
                                  null),
            DATA_NAME
        );
        // load(CompoundTag) 拿不到 server，反序列化出来的实例只有一个占位目录。
        // 每次取用时纠正为真实存档目录，否则服务器重启后 loadFromFile()/saveToFile()
        // 会读写错误路径的 waypoints.json，标点数据表现为"全部丢失"。
        manager.serverDataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return manager;
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

            // 临时标点是本局运行时数据，绝不落盘，避免崩溃 / 强停后跨局残留。
            Map<String, List<WaypointData>> persistent = new HashMap<>(waypoints);
            persistent.remove(ADVENTURER_PATH);
            String json = gson.toJson(persistent);
            
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
                // 兼容旧版本可能误写入的临时标点：读文件时一律丢弃。
                loadedWaypoints.remove(ADVENTURER_PATH);
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

    /**
     * 创建 / 刷新一个冒险家临时标点（同名覆盖，因此冷却结束后再次按技能是「移动标点」）。
     * 不落盘，调用方负责执行 {@code WaypointSync.syncToAll} 让客户端看到。
     */
    public void addAdventurerWaypoint(String name, BlockPos pos, int color) {
        List<WaypointData> list = waypoints.computeIfAbsent(ADVENTURER_PATH, k -> new ArrayList<>());
        list.removeIf(wp -> wp.getName().equals(name));
        list.add(new WaypointData(ADVENTURER_PATH, name, pos, color));
    }

    /** 清空本局所有冒险家临时标点；返回是否确实清掉了东西。 */
    public boolean clearAdventurerWaypoints() {
        return waypoints.remove(ADVENTURER_PATH) != null;
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