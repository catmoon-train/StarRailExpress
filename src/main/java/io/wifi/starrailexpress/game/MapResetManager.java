package io.wifi.starrailexpress.game;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.wifi.starrailexpress.block.FoodPlatterBlock;
import io.wifi.starrailexpress.block.NeonPillarBlock;
import io.wifi.starrailexpress.block.NeonTubeBlock;
import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.block.SprinklerBlock;
import io.wifi.starrailexpress.block.ToggleableFacingLightBlock;
import io.wifi.starrailexpress.block.TrimmedBedBlock;
import io.wifi.starrailexpress.block.VentHatchBlock;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;

public class MapResetManager {
    public static class MapResetInfos {
        public ArrayList<BlockPos> blocks;

        public MapResetInfos(ArrayList<BlockPos> blocks) {
            this.blocks = blocks;
        }
    }

    public static Gson gson = new Gson();

    public static void scanArea(ServerLevel serverWorld, AreasWorldComponent areas) {
        GameFunctions.resetPoints.clear();

        if (areas.noReset) {
            SRE.LOGGER.info("No nedd to scan: no reset flag found. " + areas.toString());
            return;
        }
        SRE.LOGGER.info("Scanning train " + areas.mapName);

        BlockPos backupMinPos = BlockPos.containing(areas.getResetTemplateArea().getMinPosition());
        BlockPos backupMaxPos = BlockPos.containing(areas.getResetTemplateArea().getMaxPosition());
        BoundingBox backupTrainBox = BoundingBox.fromCorners(backupMinPos, backupMaxPos);
        BlockPos trainMinPos = BlockPos.containing(areas.getResetPasteArea().getMinPosition());
        BlockPos trainMaxPos = trainMinPos.offset(backupTrainBox.getLength());
        BoundingBox trainBox = BoundingBox.fromCorners(trainMinPos, trainMaxPos);
        for (int k = trainBox.minZ(); k <= trainBox.maxZ(); k++) {
            for (int l = trainBox.minY(); l <= trainBox.maxY(); l++) {
                for (int m = trainBox.minX(); m <= trainBox.maxX(); m++) {
                    BlockPos blockPos6 = new BlockPos(m, l, k);
                    BlockState blockState = serverWorld.getBlockState(blockPos6);
                    if (blockState.getBlock() instanceof SmallDoorBlock) {
                        GameFunctions.resetPoints.add(blockPos6);
                    } else if (blockState.getBlock() instanceof TrimmedBedBlock) {
                        if (blockState.getValue(TrimmedBedBlock.PART).equals(BedPart.HEAD)) {
                            GameFunctions.resetPoints.add(blockPos6);
                        }
                    } else if (blockState.getBlock() instanceof FoodPlatterBlock) {
                        GameFunctions.resetPoints.add(blockPos6);

                    } else if (blockState.getBlock() instanceof LecternBlock) {
                        if (serverWorld.getBlockEntity(blockPos6) instanceof LecternBlockEntity) {
                            GameFunctions.resetPoints.add(blockPos6);
                        }
                    } else if (blockState.getBlock() instanceof SprinklerBlock) {
                        GameFunctions.resetPoints.add(blockPos6);
                    } else if (blockState.getBlock() instanceof NeonPillarBlock) {
                        GameFunctions.resetPoints.add(blockPos6);
                    } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                        GameFunctions.resetPoints.add(blockPos6);
                    } else if (blockState.getBlock() instanceof ToggleableFacingLightBlock) {
                        GameFunctions.resetPoints.add(blockPos6);
                    } else if (blockState.getBlock() instanceof VentHatchBlock) {
                        GameFunctions.resetPoints.add(blockPos6);
                    }
                }
            }
        }

    }

    public static void saveArea(ServerLevel world) {
        var areaC = AreasWorldComponent.KEY.get(world);
        Path mapsDirPath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString(),
                "map_reset_point_caches");
        File mapsDir = mapsDirPath.toFile();
        if (!mapsDir.exists()) {
            mapsDir.mkdirs();
        }
        String mapName = areaC.mapName;
        if (mapName == null)
            return;
        Path mapConfigPath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString(),
                "map_reset_point_caches", mapName + ".cache.json");
        File mapConfigFile = mapConfigPath.toFile();
        try {
            FileWriter writer = new FileWriter(mapConfigFile);
            MapResetInfos infos = new MapResetInfos(GameFunctions.resetPoints);
            gson.toJson(infos, writer);
            writer.close();
            SRE.LOGGER.info("Successfully cache reset points for map: " + mapName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadArea(ServerLevel world) {
        GameFunctions.resetPoints.clear();
        var areaC = AreasWorldComponent.KEY.get(world);
        String mapName = areaC.mapName;
        if (mapName == null)
            return;
        Path mapConfigPath = Paths.get(world.getServer().getWorldPath(LevelResource.ROOT).toString(),
                "map_reset_point_caches", mapName + ".cache.json");
        File mapConfigFile = mapConfigPath.toFile();

        // 检查地图配置文件是否存在
        if (!mapConfigFile.exists()) {
            SRE.LOGGER.warn("Map cache file does not exist: " + mapConfigFile.getAbsolutePath());
            return;
        }
        try {
            FileReader reader = new FileReader(mapConfigFile);
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            MapResetInfos mapinfos = gson.fromJson(jsonObject, MapResetInfos.class);
            GameFunctions.resetPoints = mapinfos.blocks;
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
