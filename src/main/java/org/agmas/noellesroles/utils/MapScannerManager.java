package org.agmas.noellesroles.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.MapResetManager.CachedBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MapScannerManager {
    public static class MapScannerInfo {
        public CachedBlockPos pos;
        public int type;

        public MapScannerInfo(BlockPos pos, int type) {
            this.pos = new CachedBlockPos(pos);
            this.type = type;
        }
    }

    public static class MapScannerInfos {
        public ArrayList<MapScannerInfo> infos;
        public ArrayList<String> minigameIds;
        public ArrayList<String> sabotageMinigameIds;

        public MapScannerInfos(HashMap<BlockPos, Integer> blocks) {
            infos = new ArrayList<MapScannerInfo>();
            for (var entry : blocks.entrySet()) {
                infos.add(new MapScannerInfo(entry.getKey(), entry.getValue()));
            }
            this.minigameIds = new ArrayList<>();
            this.sabotageMinigameIds = new ArrayList<>();
        }

        public MapScannerInfos(HashMap<BlockPos, Integer> blocks, HashSet<String> minigameIds) {
            this(blocks, minigameIds, new HashSet<>());
        }

        public MapScannerInfos(HashMap<BlockPos, Integer> blocks, HashSet<String> minigameIds, HashSet<String> sabotageMinigameIds) {
            infos = new ArrayList<MapScannerInfo>();
            for (var entry : blocks.entrySet()) {
                infos.add(new MapScannerInfo(entry.getKey(), entry.getValue()));
            }
            this.minigameIds = new ArrayList<>(minigameIds);
            this.sabotageMinigameIds = new ArrayList<>(sabotageMinigameIds);
        }

        public HashMap<BlockPos, Integer> getInfos() {
            var blocks = new HashMap<BlockPos, Integer>();
            for (var info : this.infos) {
                blocks.put(info.pos.getBlockPos(), info.type);
            }
            return blocks;
        }

        public HashSet<String> getMinigameIds() {
            return this.minigameIds != null ? new HashSet<>(this.minigameIds) : new HashSet<>();
        }

        public HashSet<String> getSabotageMinigameIds() {
            return this.sabotageMinigameIds != null ? new HashSet<>(this.sabotageMinigameIds) : new HashSet<>();
        }
    }

    public static Gson gson = new Gson();

    public static void loadOrScanAndSaveScannerArea(ServerLevel serverWorld, AreasWorldComponent areas) {
        if (areas.noReset) {
            SRE.LOGGER.info("No need to scan: no reset flag found. " + areas.toString());
            return;
        }
        if (loadArea(serverWorld)) {
            return;
        }
        MapScanner.scanAllTaskBlocks(serverWorld);
        saveArea(serverWorld);
    }

    public static void scanAndSaveScannerArea(ServerLevel serverWorld, AreasWorldComponent areas) {
        if (areas.noReset) {
            SRE.LOGGER.info("No need to scan: no reset flag found. " + areas.toString());
            return;
        }
        MapScanner.scanAllTaskBlocks(serverWorld);
        saveArea(serverWorld);
    }

    public static void saveArea(ServerLevel world) {
        var areaC = AreasWorldComponent.KEY.get(world);
        Path mapsDirPath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString(),
                "map_scanner_caches");
        File mapsDir = mapsDirPath.toFile();
        if (!mapsDir.exists()) {
            mapsDir.mkdirs();
        }
        String mapName = areaC.mapName;
        if (mapName == null)
            return;
        Path mapConfigPath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString(),
                "map_scanner_caches", mapName + ".cache.json");
        File dir = mapConfigPath.getParent().toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File mapConfigFile = mapConfigPath.toFile();
        try {
            FileWriter writer = new FileWriter(mapConfigFile);
            MapScannerInfos infos = new MapScannerInfos(GameUtils.taskBlocks, areaC.availableMinigameIds,
                    areaC.sabotageMinigameIds);
            gson.toJson(infos, writer);
            writer.close();
            SRE.LOGGER.info("Successfully cache scanner points for map: " + mapName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean loadArea(ServerLevel world) {
        GameUtils.taskBlocks.clear();
        var areaC = AreasWorldComponent.KEY.get(world);
        String mapName = areaC.mapName;
        if (mapName == null)
            return false;
        Path mapConfigPath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString(),
                "map_scanner_caches", mapName + ".cache.json");
        File mapConfigFile = mapConfigPath.toFile();

        // 检查地图配置文件是否存在
        if (!mapConfigFile.exists()) {
            SRE.LOGGER.warn("Map scanner cache file does not exist: " + mapConfigFile.getAbsolutePath());
            return false;
        }
        try {
            FileReader reader = new FileReader(mapConfigFile);
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            MapScannerInfos mapinfos = gson.fromJson(jsonObject, MapScannerInfos.class);
            if (mapinfos.sabotageMinigameIds == null) {
                reader.close();
                return false;
            }
            GameUtils.taskBlocks = mapinfos.getInfos();
            // 恢复可用小游戏种类 ID
            areaC.availableMinigameIds.clear();
            areaC.availableMinigameIds.addAll(mapinfos.getMinigameIds());
            areaC.sabotageMinigameIds.clear();
            areaC.sabotageMinigameIds.addAll(mapinfos.getSabotageMinigameIds());
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
