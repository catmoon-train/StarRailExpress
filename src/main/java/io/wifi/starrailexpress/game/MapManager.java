package io.wifi.starrailexpress.game;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.data.MapConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapManager {
    private static final Gson gson = new Gson();
    private static final Random random = new Random();

    /**
     * 保存当前地图配置到指定的地图文件
     * 
     * @param serverWorld 服务器世界
     * @param mapName     地图名称
     * @return 是否成功保存
     */
    public static boolean saveCurrentMap(ServerLevel serverWorld, String mapName) {
        try {
            // 获取AreasWorldComponent中的当前配置
            AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);

            // 创建地图目录
            Path mapsDirPath = Paths.get(serverWorld.getServer().getWorldPath(LevelResource.ROOT).toString(),
                    "train_maps");
            File mapsDir = mapsDirPath.toFile();
            if (!mapsDir.exists()) {
                mapsDir.mkdirs();
            }

            // 构建地图配置文件路径
            Path mapConfigPath = Paths.get(mapsDirPath.toString(), mapName + ".json");
            File mapConfigFile = mapConfigPath.toFile();

            // 创建JSON对象并填充当前地图配置，使用新的嵌套结构
            JsonObject jsonObject = new JsonObject();

            // 保存出生点位置 - 使用嵌套对象
            JsonObject spawnPosObj = new JsonObject();
            spawnPosObj.addProperty("x", areas.getSpawnPos().pos.x());
            spawnPosObj.addProperty("y", areas.getSpawnPos().pos.y());
            spawnPosObj.addProperty("z", areas.getSpawnPos().pos.z());
            spawnPosObj.addProperty("yaw", areas.getSpawnPos().yaw);
            spawnPosObj.addProperty("pitch", areas.getSpawnPos().pitch);
            jsonObject.add("spawnPos", spawnPosObj);

            // 保存观战者出生点位置 - 使用嵌套对象
            JsonObject spectatorSpawnPosObj = new JsonObject();
            spectatorSpawnPosObj.addProperty("x", areas.getSpectatorSpawnPos().pos.x());
            spectatorSpawnPosObj.addProperty("y", areas.getSpectatorSpawnPos().pos.y());
            spectatorSpawnPosObj.addProperty("z", areas.getSpectatorSpawnPos().pos.z());
            spectatorSpawnPosObj.addProperty("yaw", areas.getSpectatorSpawnPos().yaw);
            spectatorSpawnPosObj.addProperty("pitch", areas.getSpectatorSpawnPos().pitch);
            jsonObject.add("spectatorSpawnPos", spectatorSpawnPosObj);

            // 保存准备区域 - 使用嵌套对象
            JsonObject readyAreaObj = new JsonObject();
            readyAreaObj.addProperty("minX", areas.getReadyArea().minX);
            readyAreaObj.addProperty("minY", areas.getReadyArea().minY);
            readyAreaObj.addProperty("minZ", areas.getReadyArea().minZ);
            readyAreaObj.addProperty("maxX", areas.getReadyArea().maxX);
            readyAreaObj.addProperty("maxY", areas.getReadyArea().maxY);
            readyAreaObj.addProperty("maxZ", areas.getReadyArea().maxZ);
            jsonObject.add("readyArea", readyAreaObj);

            // 保存游戏区域偏移 - 使用嵌套对象
            JsonObject playAreaOffsetObj = new JsonObject();
            playAreaOffsetObj.addProperty("x", areas.getPlayAreaOffset().x());
            playAreaOffsetObj.addProperty("y", areas.getPlayAreaOffset().y());
            playAreaOffsetObj.addProperty("z", areas.getPlayAreaOffset().z());
            jsonObject.add("playAreaOffset", playAreaOffsetObj);

            // 保存游戏区域 - 使用嵌套对象
            JsonObject playAreaObj = new JsonObject();
            playAreaObj.addProperty("minX", areas.getPlayArea().minX);
            playAreaObj.addProperty("minY", areas.getPlayArea().minY);
            playAreaObj.addProperty("minZ", areas.getPlayArea().minZ);
            playAreaObj.addProperty("maxX", areas.getPlayArea().maxX);
            playAreaObj.addProperty("maxY", areas.getPlayArea().maxY);
            playAreaObj.addProperty("maxZ", areas.getPlayArea().maxZ);
            jsonObject.add("playArea", playAreaObj);

            // 保存重置粘贴区域 - 使用嵌套对象
            JsonObject resetPasteAreaObj = new JsonObject();
            resetPasteAreaObj.addProperty("minX", areas.getResetPasteArea().minX);
            resetPasteAreaObj.addProperty("minY", areas.getResetPasteArea().minY);
            resetPasteAreaObj.addProperty("minZ", areas.getResetPasteArea().minZ);
            resetPasteAreaObj.addProperty("maxX", areas.getResetPasteArea().maxX);
            resetPasteAreaObj.addProperty("maxY", areas.getResetPasteArea().maxY);
            resetPasteAreaObj.addProperty("maxZ", areas.getResetPasteArea().maxZ);
            jsonObject.add("resetPasteArea", resetPasteAreaObj);

            // 保存重置模板区域 - 使用嵌套对象
            JsonObject resetTemplateAreaObj = new JsonObject();
            resetTemplateAreaObj.addProperty("minX", areas.getResetTemplateArea().minX);
            resetTemplateAreaObj.addProperty("minY", areas.getResetTemplateArea().minY);
            resetTemplateAreaObj.addProperty("minZ", areas.getResetTemplateArea().minZ);
            resetTemplateAreaObj.addProperty("maxX", areas.getResetTemplateArea().maxX);
            resetTemplateAreaObj.addProperty("maxY", areas.getResetTemplateArea().maxY);
            resetTemplateAreaObj.addProperty("maxZ", areas.getResetTemplateArea().maxZ);
            jsonObject.add("resetTemplateArea", resetTemplateAreaObj);

            // 保存房间数量
            jsonObject.addProperty("roomCount", areas.getRoomCount());

            // 保存房间位置 - 使用嵌套对象
            JsonObject roomPositionsObj = new JsonObject();
            for (int i = 1; i <= areas.getRoomCount(); i++) {
                Vec3 roomPos = areas.getRoomPosition(i);
                if (roomPos != null) {
                    JsonObject posObj = new JsonObject();
                    posObj.addProperty("x", roomPos.x());
                    posObj.addProperty("y", roomPos.y());
                    posObj.addProperty("z", roomPos.z());
                    roomPositionsObj.add(String.valueOf(i), posObj);
                }
            }
            jsonObject.add("roomPositions", roomPositionsObj);

            // 保存场景偏移配置
            JsonObject sceneOffsetObj = new JsonObject();
            sceneOffsetObj.addProperty("enabled", areas.sceneOffsetEnabled);
            sceneOffsetObj.addProperty("x", areas.sceneOffsetX);
            sceneOffsetObj.addProperty("y", areas.sceneOffsetY);
            sceneOffsetObj.addProperty("z", areas.sceneOffsetZ);
            jsonObject.add("sceneOffset", sceneOffsetObj);

            // 写入文件
            FileWriter writer = new FileWriter(mapConfigFile);
            gson.toJson(jsonObject, writer);
            writer.close();

            SRE.LOGGER.info("Successfully saved map: " + mapName);
            return true;
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to save map: " + mapName, e);
            return false;
        }
    }

    public static String last_start_map = "";

    /**
     * 加载指定的地图配置
     * 
     * @param serverWorld 服务器世界
     * @param mapName     地图名称
     * @return 是否成功加载
     */
    public static boolean loadMap(ServerLevel serverWorld, String mapName) {
        try {
            // 构建地图配置文件路径
            Path mapConfigPath = Paths.get(serverWorld.getServer().getWorldPath(LevelResource.ROOT).toString(),
                    "train_maps", mapName + ".json");
            File mapConfigFile = mapConfigPath.toFile();

            // 检查地图配置文件是否存在
            if (!mapConfigFile.exists()) {
                SRE.LOGGER.warn("Map configuration file does not exist: " + mapConfigFile.getAbsolutePath());
                return false;
            }

            // 获取AreasWorldComponent
            AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);

            // 读取JSON文件
            FileReader reader = new FileReader(mapConfigFile);
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
            areas.mapName = mapName;
            if (jsonObject.has("noReset")) {
                areas.noReset = jsonObject.get("noReset").getAsBoolean();
            } else {
                areas.noReset = false;
            }

            if (jsonObject.has("haveOutsideSound")) {
                areas.haveOutsideSound = jsonObject.get("haveOutsideSound").getAsBoolean();
            } else {
                areas.haveOutsideSound = false;
            }
            if (jsonObject.has("canJump")) {
                areas.canJump = jsonObject.get("canJump").getAsBoolean();
            } else {
                areas.canJump = false;
            }
            if (jsonObject.has("sceneScroll")) {
                String scrollWay = jsonObject.get("sceneScroll").getAsString();
                switch (scrollWay) {
                    case "X":
                        areas.SceneScrollAxis = AreasWorldComponent.ScrollAxis.X;
                        break;
                    case "Y":
                        areas.SceneScrollAxis = AreasWorldComponent.ScrollAxis.Y;
                        break;
                    case "Z":
                        areas.SceneScrollAxis = AreasWorldComponent.ScrollAxis.Z;
                        break;
                    default:
                        areas.SceneScrollAxis = AreasWorldComponent.ScrollAxis.NONE;
                }
            } else {
                areas.SceneScrollAxis = AreasWorldComponent.ScrollAxis.NONE;
            }

            if (jsonObject.has("canSwim")) {
                areas.canSwim = jsonObject.get("canSwim").getAsBoolean();
            } else {
                areas.canSwim = false;
            }

            // 加载场景偏移配置（默认关闭）
            if (jsonObject.has("sceneOffset")) {
                JsonObject sceneOffsetObj = jsonObject.getAsJsonObject("sceneOffset");
                areas.sceneOffsetEnabled = sceneOffsetObj.has("enabled") && sceneOffsetObj.get("enabled").getAsBoolean();
                areas.sceneOffsetX = sceneOffsetObj.has("x") ? sceneOffsetObj.get("x").getAsDouble() : 0;
                areas.sceneOffsetY = sceneOffsetObj.has("y") ? sceneOffsetObj.get("y").getAsDouble() : 125;
                areas.sceneOffsetZ = sceneOffsetObj.has("z") ? sceneOffsetObj.get("z").getAsDouble() : 0;
                SRE.LOGGER.info("Loaded scene offset: enabled=" + areas.sceneOffsetEnabled +
                        ", x=" + areas.sceneOffsetX + ", y=" + areas.sceneOffsetY + ", z=" + areas.sceneOffsetZ);
            } else {
                areas.sceneOffsetEnabled = false;
                areas.sceneOffsetX = 0;
                areas.sceneOffsetY = 125;
                areas.sceneOffsetZ = 0;
            }

            // 应用配置到AreasWorldComponent，使用新的嵌套结构
            if (jsonObject.has("spawnPos")) {
                JsonObject spawnPosObj = jsonObject.getAsJsonObject("spawnPos");
                float spawnYaw = spawnPosObj.has("yaw") ? spawnPosObj.get("yaw").getAsFloat() : 0f;
                float spawnPitch = spawnPosObj.has("pitch") ? spawnPosObj.get("pitch").getAsFloat() : 0f;
                areas.setSpawnPos(new AreasWorldComponent.PosWithOrientation(
                        spawnPosObj.get("x").getAsDouble(),
                        spawnPosObj.get("y").getAsDouble(),
                        spawnPosObj.get("z").getAsDouble(),
                        spawnYaw,
                        spawnPitch));
                SRE.LOGGER.info("Loaded spawn position: " + spawnPosObj.get("x").getAsDouble() + ", " +
                        spawnPosObj.get("y").getAsDouble() + ", " + spawnPosObj.get("z").getAsDouble());
            } else {
                areas.setSpawnPos(null);
                SRE.LOGGER.warn("Missing spawn position data in map config: " + mapName);
            }

            if (jsonObject.has("spectatorSpawnPos")) {
                JsonObject spectatorSpawnPosObj = jsonObject.getAsJsonObject("spectatorSpawnPos");
                float spectatorSpawnYaw = spectatorSpawnPosObj.has("yaw") ? spectatorSpawnPosObj.get("yaw").getAsFloat()
                        : 0f;
                float spectatorSpawnPitch = spectatorSpawnPosObj.has("pitch")
                        ? spectatorSpawnPosObj.get("pitch").getAsFloat()
                        : 0f;
                areas.setSpectatorSpawnPos(new AreasWorldComponent.PosWithOrientation(
                        spectatorSpawnPosObj.get("x").getAsDouble(),
                        spectatorSpawnPosObj.get("y").getAsDouble(),
                        spectatorSpawnPosObj.get("z").getAsDouble(),
                        spectatorSpawnYaw,
                        spectatorSpawnPitch));
                SRE.LOGGER
                        .info("Loaded spectator spawn position: " + spectatorSpawnPosObj.get("x").getAsDouble() + ", " +
                                spectatorSpawnPosObj.get("y").getAsDouble() + ", "
                                + spectatorSpawnPosObj.get("z").getAsDouble());
            } else {
                SRE.LOGGER.warn("Missing spectator spawn position data in map config: " + mapName);
            }

            if (jsonObject.has("readyArea")) {
                JsonObject readyAreaObj = jsonObject.getAsJsonObject("readyArea");
                areas.setReadyArea(new AABB(
                        readyAreaObj.get("minX").getAsDouble(),
                        readyAreaObj.get("minY").getAsDouble(),
                        readyAreaObj.get("minZ").getAsDouble(),
                        readyAreaObj.get("maxX").getAsDouble(),
                        readyAreaObj.get("maxY").getAsDouble(),
                        readyAreaObj.get("maxZ").getAsDouble()));
                SRE.LOGGER.info("Loaded ready area: " + readyAreaObj.get("minX").getAsDouble() + "," +
                        readyAreaObj.get("minY").getAsDouble() + "," + readyAreaObj.get("minZ").getAsDouble() + " to " +
                        readyAreaObj.get("maxX").getAsDouble() + "," + readyAreaObj.get("maxY").getAsDouble() + "," +
                        readyAreaObj.get("maxZ").getAsDouble());
            } else {
                SRE.LOGGER.warn("Missing ready area data in map config: " + mapName);
            }

            if (jsonObject.has("playAreaOffset")) {
                JsonObject playAreaOffsetObj = jsonObject.getAsJsonObject("playAreaOffset");
                areas.setPlayAreaOffset(new Vec3(
                        playAreaOffsetObj.get("x").getAsDouble(),
                        playAreaOffsetObj.get("y").getAsDouble(),
                        playAreaOffsetObj.get("z").getAsDouble()));
                SRE.LOGGER.info("Loaded play area offset: " + playAreaOffsetObj.get("x").getAsDouble() + ", " +
                        playAreaOffsetObj.get("y").getAsDouble() + ", " + playAreaOffsetObj.get("z").getAsDouble());
            } else {
                SRE.LOGGER.warn("Missing play area offset data in map config: " + mapName);
            }
            if (jsonObject.has("playArea")) {

                JsonObject playAreaObj = jsonObject.getAsJsonObject("playArea");
                areas.setPlayArea(new AABB(
                        playAreaObj.get("minX").getAsDouble(),
                        playAreaObj.get("minY").getAsDouble(),
                        playAreaObj.get("minZ").getAsDouble(),
                        playAreaObj.get("maxX").getAsDouble(),
                        playAreaObj.get("maxY").getAsDouble(),
                        playAreaObj.get("maxZ").getAsDouble()));
                SRE.LOGGER.info("Loaded play area: " + playAreaObj.get("minX").getAsDouble() + "," +
                        playAreaObj.get("minY").getAsDouble() + "," + playAreaObj.get("minZ").getAsDouble() + " to " +
                        playAreaObj.get("maxX").getAsDouble() + "," + playAreaObj.get("maxY").getAsDouble() + "," +
                        playAreaObj.get("maxZ").getAsDouble());
            } else {
                SRE.LOGGER.warn("Missing play area data in map config: " + mapName);
            }

            if (jsonObject.has("sceneArea")) {
                JsonObject sceneAreaObj = jsonObject.getAsJsonObject("sceneArea");
                areas.setSceneArea(new AABB(
                        sceneAreaObj.get("minX").getAsDouble(),
                        sceneAreaObj.get("minY").getAsDouble(),
                        sceneAreaObj.get("minZ").getAsDouble(),
                        sceneAreaObj.get("maxX").getAsDouble(),
                        sceneAreaObj.get("maxY").getAsDouble(),
                        sceneAreaObj.get("maxZ").getAsDouble()));
                SRE.LOGGER.info("Loaded 'sceneArea': " + sceneAreaObj.get("minX").getAsDouble() + "," +
                        sceneAreaObj.get("minY").getAsDouble() + "," + sceneAreaObj.get("minZ").getAsDouble() + " to " +
                        sceneAreaObj.get("maxX").getAsDouble() + "," + sceneAreaObj.get("maxY").getAsDouble() + "," +
                        sceneAreaObj.get("maxZ").getAsDouble());
            } else {
                areas.setSceneArea(areas.getPlayArea());
                SRE.LOGGER.warn("Missing 'sceneArea' data in map config: " + mapName);
            }
            if (jsonObject.has("resetTemplateArea")) {
                JsonObject resetTemplateAreaObj = jsonObject.getAsJsonObject("resetTemplateArea");
                areas.setResetTemplateArea(new AABB(
                        resetTemplateAreaObj.get("minX").getAsDouble(),
                        resetTemplateAreaObj.get("minY").getAsDouble(),
                        resetTemplateAreaObj.get("minZ").getAsDouble(),
                        resetTemplateAreaObj.get("maxX").getAsDouble(),
                        resetTemplateAreaObj.get("maxY").getAsDouble(),
                        resetTemplateAreaObj.get("maxZ").getAsDouble()));
                SRE.LOGGER.info("Loaded reset template area: " + resetTemplateAreaObj.get("minX").getAsDouble() + "," +
                        resetTemplateAreaObj.get("minY").getAsDouble() + ","
                        + resetTemplateAreaObj.get("minZ").getAsDouble() + " to " +
                        resetTemplateAreaObj.get("maxX").getAsDouble() + ","
                        + resetTemplateAreaObj.get("maxY").getAsDouble() + "," +
                        resetTemplateAreaObj.get("maxZ").getAsDouble());
            } else {
                SRE.LOGGER.warn("Missing reset template area data in map config: " + mapName);
            }

            if (jsonObject.has("resetPasteArea")) {
                JsonObject resetPasteAreaObj = jsonObject.getAsJsonObject("resetPasteArea");
                areas.setResetPasteArea(new AABB(
                        resetPasteAreaObj.get("minX").getAsDouble(),
                        resetPasteAreaObj.get("minY").getAsDouble(),
                        resetPasteAreaObj.get("minZ").getAsDouble(),
                        resetPasteAreaObj.get("maxX").getAsDouble(),
                        resetPasteAreaObj.get("maxY").getAsDouble(),
                        resetPasteAreaObj.get("maxZ").getAsDouble()));
                SRE.LOGGER.info("Loaded reset paste area: " + resetPasteAreaObj.get("minX").getAsDouble() + "," +
                        resetPasteAreaObj.get("minY").getAsDouble() + "," + resetPasteAreaObj.get("minZ").getAsDouble()
                        + " to " +
                        resetPasteAreaObj.get("maxX").getAsDouble() + "," + resetPasteAreaObj.get("maxY").getAsDouble()
                        + "," +
                        resetPasteAreaObj.get("maxZ").getAsDouble());
            } else {
                SRE.LOGGER.warn("Missing reset paste area data in map config: " + mapName);
            }
            areas.disabledTasks.clear();
            if (jsonObject.has("disabledTasks")) {
                var jsonArr = jsonObject.get("disabledTasks").getAsJsonArray();
                for (JsonElement data : jsonArr.asList()) {
                    areas.disabledTasks.add(data.getAsString());
                }
            } else {
                areas.disabledTasks.add("BREATHE");
            }
            if (jsonObject.has("roomCount")) {
                int roomCount = jsonObject.get("roomCount").getAsInt();
                areas.setRoomCount(roomCount);
                SRE.LOGGER.info("Loaded room count: " + roomCount);
            } else {
                SRE.LOGGER.warn("Missing room count data in map config: " + mapName);
            }

            if (jsonObject.has("roomPositions")) {
                JsonObject roomPositionsObj = jsonObject.getAsJsonObject("roomPositions");
                areas.getRoomPositions().clear();
                for (String key : roomPositionsObj.keySet()) {
                    try {
                        int roomNumber = Integer.parseInt(key);
                        JsonObject posObj = roomPositionsObj.getAsJsonObject(key);
                        if (posObj.has("x") && posObj.has("y") && posObj.has("z")) {
                            Vec3 position = new Vec3(
                                    posObj.get("x").getAsDouble(),
                                    posObj.get("y").getAsDouble(),
                                    posObj.get("z").getAsDouble());
                            areas.getRoomPositions().put(roomNumber, position);
                            SRE.LOGGER.debug("Loaded room " + roomNumber + " position: " + position.x() + ", "
                                    + position.y() + ", " + position.z());
                        } else {
                            SRE.LOGGER.warn("Invalid position data for room " + key + " in map config: " + mapName);
                        }
                    } catch (NumberFormatException e) {
                        SRE.LOGGER.warn("Invalid room number in map config: " + key);
                    }
                }
            } else {
                SRE.LOGGER.warn("Missing room positions data in map config: " + mapName);
            }

            // 同步到客户端
            areas.sync();
            last_start_map = mapName;

            SRE.LOGGER.info("Successfully loaded map: " + mapName);
            return true;
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to load map: " + mapName, e);
            return false;
        }
    }

    /**
     * 随机加载一个可用的地图配置
     * 
     * @param serverWorld 服务器世界
     * @return 是否成功加载随机地图
     */
    public static boolean loadRandomMap(ServerLevel serverWorld) {
        List<String> availableMaps = getAvailableMaps(serverWorld);
        availableMaps.removeIf(
                e -> {
                    final var first = MapConfig.getInstance().maps.stream().filter(mapEntry -> mapEntry.id.equals(e))
                            .findFirst();
                    AtomicBoolean isAvailable = new AtomicBoolean(false);
                    first.ifPresent(
                            a -> {
                                isAvailable.set(!a.canSelect || first.get().maxCount >= serverWorld.players().size());
                            });
                    return isAvailable.get();
                });

        if (availableMaps.isEmpty()) {
            SRE.LOGGER.warn("No maps available to load randomly");
            return false;
        }

        // 随机选择一个地图
        String randomMap = availableMaps.get(random.nextInt(availableMaps.size()));
        SRE.LOGGER.info("Randomly selected map: " + randomMap);

        return loadMap(serverWorld, randomMap);
    }

    /**
     * 获取所有可用的地图列表
     * 
     * @param serverWorld 服务器世界
     * @return 可用地图名称列表
     */
    public static List<String> getAvailableMaps(ServerLevel serverWorld) {
        List<String> maps = new ArrayList<>();

        try {
            Path mapsDirPath = Paths.get(serverWorld.getServer().getWorldPath(LevelResource.ROOT).toString(),
                    "train_maps");
            File mapsDir = mapsDirPath.toFile();

            if (mapsDir.exists() && mapsDir.isDirectory()) {
                File[] files = mapsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                if (files != null) {
                    for (File file : files) {
                        String fileName = file.getName();
                        String mapName = fileName.substring(0, fileName.length() - 5); // 移除.json后缀
                        maps.add(mapName);
                    }
                }
            }
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to list available maps", e);
        }

        return maps;
    }
}