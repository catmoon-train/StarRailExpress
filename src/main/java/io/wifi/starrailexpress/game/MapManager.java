package io.wifi.starrailexpress.game;

import com.google.gson.*;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.scenery.SceneAsset;
import io.wifi.starrailexpress.scenery.server.SceneLibrary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapManager {
    private static final long MAX_IMPORTED_MAP_BYTES = 8L * 1024L * 1024L;
    private static final Gson gson = new Gson();
    private static final Gson prettyGson = new GsonBuilder()
            .setPrettyPrinting() // 关键步骤：启用格式化
            .create();
    private static final Random random = new Random();

    public static void updateSceneAssetMetadata(ServerLevel serverWorld, String mapName, String hash,
            AreasWorldComponent.ScrollAxis axis) throws IOException {
        Path mapsDir = serverWorld.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("train_maps")
                .toAbsolutePath()
                .normalize();
        Path mapConfigPath = mapsDir.resolve(mapName + ".json").normalize();
        if (!mapConfigPath.startsWith(mapsDir) || !Files.isRegularFile(mapConfigPath)) {
            throw new IOException("Invalid map name or missing map config: " + mapName);
        }
        JsonObject root;
        try (FileReader reader = new FileReader(mapConfigPath.toFile(), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        if (!areas.getSceneId().isBlank()) {
            SceneLibrary.Result result = SceneLibrary.updateCurrent(serverWorld);
            if (!result.success()) {
                throw new IOException(result.message());
            }
            setSceneReference(root, areas.getSceneId());
        } else {
            root.addProperty("sceneScroll", axis.name());
            root.add("sceneDisplayOffset", vec3Json(areas.getSceneDisplayOffset()));
            root.add("sceneAsset", sceneAssetJson(areas, hash));
        }

        Path temp = mapConfigPath.resolveSibling(mapConfigPath.getFileName() + ".tmp");
        Files.writeString(temp, prettyGson.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, mapConfigPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, mapConfigPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void updateMapSceneReference(ServerLevel serverWorld, String mapName, String sceneId)
            throws IOException {
        Path mapConfigPath = resolveMapConfigPath(serverWorld, mapName);
        if (!Files.isRegularFile(mapConfigPath)) {
            throw new IOException("地图配置不存在: " + mapName);
        }
        JsonObject root;
        try (FileReader reader = new FileReader(mapConfigPath.toFile(), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        setSceneReference(root, sceneId);
        Path temp = mapConfigPath.resolveSibling(mapConfigPath.getFileName() + ".scene.tmp");
        Files.writeString(temp, prettyGson.toJson(root), StandardCharsets.UTF_8);
        atomicMove(temp, mapConfigPath);
    }

    /**
     * 删除指定地图文件
     * 
     * @param serverWorld 服务器世界
     * @param mapName     地图名称
     * @return 是否成功删除
     */
    public static boolean removeMapWithoutTry(ServerLevel serverWorld, String mapName)
            throws Exception {
        Path mapConfigPath = resolveMapConfigPath(serverWorld, mapName);
        if (!Files.isRegularFile(mapConfigPath)) {
            return false;
        }
        return Files.deleteIfExists(mapConfigPath);
    }

    /**
     * 保存当前地图配置到指定的地图文件
     * 
     * @param serverWorld   服务器世界
     * @param mapName       地图名称
     * @param overwriteFile 是否覆盖文件
     * @return 是否成功保存
     */
    public static boolean saveCurrentMapWithoutTry(ServerLevel serverWorld, String mapName, boolean overwriteFile)
            throws Exception {
        // 获取AreasWorldComponent中的当前配置
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);

        Path mapConfigPath = resolveMapConfigPath(serverWorld, mapName);
        File mapConfigFile = mapConfigPath.toFile();
        if (mapConfigFile.exists() && !overwriteFile) {
            return false;
        }
        if (!mapConfigFile.getParentFile().exists()) {
            mapConfigFile.getParentFile().mkdirs();
        }
        // 创建JSON对象并填充当前地图配置，使用新的嵌套结构
        JsonObject jsonObject = new JsonObject();
        if (areas.areasSettings == null)
            areas.areasSettings = new AreasSettings();
        jsonObject.add("settings", gson.toJsonTree(areas.areasSettings));

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

        if (!areas.getSceneId().isBlank()) {
            jsonObject.addProperty("scene", areas.getSceneId());
        } else if (areas.isSceneAreaConfigured()) {
            JsonObject sceneAreaObj = new JsonObject();
            sceneAreaObj.addProperty("minX", areas.getSceneArea().minX);
            sceneAreaObj.addProperty("minY", areas.getSceneArea().minY);
            sceneAreaObj.addProperty("minZ", areas.getSceneArea().minZ);
            sceneAreaObj.addProperty("maxX", areas.getSceneArea().maxX);
            sceneAreaObj.addProperty("maxY", areas.getSceneArea().maxY);
            sceneAreaObj.addProperty("maxZ", areas.getSceneArea().maxZ);
            jsonObject.add("sceneArea", sceneAreaObj);
            jsonObject.addProperty("sceneScroll", areas.getSceneScroll().name());
            jsonObject.add("sceneDisplayOffset", vec3Json(areas.getSceneDisplayOffset()));
            if (!areas.getSceneAssetHash().isBlank()
                    || !areas.getSceneAssetRemoteUrl().isBlank()
                    || areas.isSceneAssetTrusted()) {
                JsonObject sceneAssetObj = new JsonObject();
                sceneAssetObj.addProperty("schema", SceneAsset.CURRENT_SCHEMA);
                if (!areas.getSceneAssetHash().isBlank()) {
                    sceneAssetObj.addProperty("sha256", areas.getSceneAssetHash());
                }
                if (!areas.getSceneAssetRemoteUrl().isBlank()) {
                    sceneAssetObj.addProperty("url", areas.getSceneAssetRemoteUrl());
                }
                sceneAssetObj.addProperty("trusted", areas.isSceneAssetTrusted());
                jsonObject.add("sceneAsset", sceneAssetObj);
            }
        }

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
        jsonObject.add("disabledTasks", gson.toJsonTree(areas.disabledTasks));
        jsonObject.add("disabledRoles", gson.toJsonTree(areas.disabledRoles));
        jsonObject.add("disabledModifiers", gson.toJsonTree(areas.disabledModifiers));
        jsonObject.add("enableSceneTask", gson.toJsonTree(areas.enableSceneTask));
        // 保存支持的游戏模式列表
        jsonObject.add("gameModes", gson.toJsonTree(areas.gameModes));

        // 写入文件
        Path temp = mapConfigPath.resolveSibling(mapConfigPath.getFileName() + ".save.tmp");
        Files.writeString(temp, prettyGson.toJson(jsonObject), StandardCharsets.UTF_8);
        atomicMove(temp, mapConfigPath);

        areas.mapName = normalizedMapName(mapName);
        areas.sync();
        SRE.LOGGER.info("Successfully saved map: " + mapName);
        return true;
    }

    public static boolean saveCurrentMap(ServerLevel serverWorld, String mapName, boolean overwriteFile) {
        try {
            return saveCurrentMapWithoutTry(serverWorld, mapName, overwriteFile);
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
            Path mapConfigPath = resolveMapConfigPath(serverWorld, mapName);
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
            areas.mapName = normalizedMapName(mapName);
            // 先读取，避免后面被覆盖了
            if (jsonObject.has("settings")) {
                try {
                    areas.areasSettings = gson.fromJson(jsonObject.get("settings"), AreasSettings.class);
                } catch (Exception e) {
                    areas.areasSettings = new AreasSettings();
                    SRE.LOGGER.error("Error while loading map config 'settings'", e);
                }
            } else {
                areas.areasSettings = new AreasSettings();
            }

            if (jsonObject.has("noReset")) {
                areas.areasSettings.noReset = jsonObject.get("noReset").getAsBoolean();
            }

            if (jsonObject.has("mustCopy")) {
                areas.areasSettings.mustCopy = jsonObject.get("mustCopy").getAsBoolean();
            }
            if (jsonObject.has("haveOutsideSound")) {
                areas.areasSettings.haveOutsideSound = jsonObject.get("haveOutsideSound").getAsBoolean();
            }
            if (jsonObject.has("sceneOutsideSound")) {
                areas.areasSettings.sceneOutsideSound = AreasSettings.BackgroundAmbienceSound
                        .valueOf(jsonObject.get("sceneOutsideSound").getAsString());
                if (areas.areasSettings.sceneOutsideSound == null) {

                    areas.areasSettings.sceneOutsideSound = AreasSettings.BackgroundAmbienceSound.train;
                }
            }

            // 旧版格式兼容
            if (jsonObject.has("fallToDeathHeight")) {
                areas.areasSettings.fallToDeathHeight = jsonObject.get("fallToDeathHeight").getAsInt();
            }
            // 旧版格式兼容

            if (jsonObject.has("canJump")) {
                areas.areasSettings.canJump = jsonObject.get("canJump").getAsBoolean();
            }
            // 旧版格式兼容

            if (jsonObject.has("canSwim")) {
                areas.areasSettings.canSimpleSwim = jsonObject.get("canSwim").getAsBoolean();
            }
            // 旧版格式兼容
            if (jsonObject.has("enableOxygenDrowning")) {
                areas.areasSettings.enableOxygenDrowning = jsonObject.get("enableOxygenDrowning").getAsBoolean();
            }
            // 旧版格式兼容
            if (jsonObject.has("mapStatusBar")) {
                areas.areasSettings.mapStatusBar = io.wifi.starrailexpress.game.data.MapStatusBarType
                        .byName(jsonObject.get("mapStatusBar").getAsString());
            }

            // 加载雪花效果配置（默认关闭）
            // 旧版格式兼容
            if (jsonObject.has("snowEnabled")) {
                areas.areasSettings.snowEnabled = jsonObject.get("snowEnabled").getAsBoolean();
            }
            // 加载沙尘暴效果配置（默认关闭）
            // 旧版格式兼容
            if (jsonObject.has("sandEnabled")) {
                areas.areasSettings.sandEnabled = jsonObject.get("sandEnabled").getAsBoolean();
            }
            // 旧版格式兼容
            if (jsonObject.has("fogEnabled")) {
                areas.areasSettings.fogEnabled = jsonObject.get("fogEnabled").getAsBoolean();
            }
            // 旧版格式兼容
            if (jsonObject.has("fogEnd")) {
                areas.areasSettings.fogEnd = jsonObject.get("fogEnd").getAsFloat();
            }
            if (jsonObject.has("fogShape")) {
                String fogShape = jsonObject.get("fogShape").getAsString();
                fogShape = fogShape.toUpperCase();
                areas.areasSettings.fogShape = AreasSettings.FogShape.valueOf(fogShape);
            }

            // 加载天气配置（默认晴天）
            // 旧版兼容
            if (jsonObject.has("weather")) {
                areas.areasSettings.weather = AreasSettings.MinecraftWeather
                        .valueOf(jsonObject.get("weather").getAsString());
            }

            // 加载重力配置（默认0.08）
            // 旧版兼容
            if (jsonObject.has("gravity")) {
                areas.areasSettings.gravityModifier = jsonObject.get("gravity").getAsDouble() - 0.08;
                SRE.LOGGER.info("Loaded old gravity modifier config: " + areas.areasSettings.gravityModifier);
            }

            // 加载药水效果配置（默认空数组）
            if (jsonObject.has("effect")) {
                areas.areasSettings.mobEffects = new java.util.ArrayList<>();
                var effectElement = jsonObject.get("effect");
                if (effectElement.isJsonArray()) {
                    for (var e : effectElement.getAsJsonArray()) {
                        areas.areasSettings.mobEffects.add(e.getAsString());
                    }
                } else if (effectElement.isJsonPrimitive()) {
                    // 兼容旧格式：单个字符串 "namespace:id,level"
                    String oldFormat = effectElement.getAsString();
                    if (!oldFormat.isEmpty()) {
                        areas.areasSettings.mobEffects.add(oldFormat);
                    }
                }
                SRE.LOGGER.info("Loaded Old Effect Settings: {}", areas.areasSettings.mobEffects);
            }

            // 加载时间配置（默认午夜 18000）
            // 旧版兼容
            if (jsonObject.has("time")) {
                areas.areasSettings.time = jsonObject.get("time").getAsLong();
            }

            // 加载昼夜循环配置（默认关闭）
            // 旧版兼容
            if (jsonObject.has("daylightCycle")) {
                areas.areasSettings.daylightCycle = jsonObject.get("daylightCycle").getAsBoolean();
            }

            // 加载天气循环配置（默认关闭）
            // 旧版兼容
            if (jsonObject.has("weatherCycle")) {
                areas.areasSettings.weatherCycle = jsonObject.get("weatherCycle").getAsBoolean();
            }

            // 加载小游戏任务系统开关（默认关闭）
            if (jsonObject.has("minigameQuestEnabled")) {
                areas.areasSettings.minigameQuestEnabled = jsonObject.get("minigameQuestEnabled").getAsBoolean();
            }

            // 加载地图初始物品配置
            if (jsonObject.has("initialItems")) {
                areas.areasSettings.initialItems = new java.util.ArrayList<>();
                var iiElement = jsonObject.get("initialItems");
                if (iiElement.isJsonArray()) {
                    for (var e : iiElement.getAsJsonArray()) {
                        areas.areasSettings.initialItems.add(e.getAsString());
                    }
                }
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

            if (jsonObject.has("scene") && jsonObject.get("scene").isJsonPrimitive()) {
                String sceneId = jsonObject.get("scene").getAsString().trim();
                SceneLibrary.Result result = SceneLibrary.loadInto(serverWorld, sceneId);
                if (!result.success()) {
                    SceneLibrary.clearScene(areas);
                    areas.setSceneId(sceneId);
                    SRE.LOGGER.warn("Map {} references unavailable scene {}: {}",
                            mapName, sceneId, result.message());
                }
            } else if (jsonObject.has("sceneArea")) {
                JsonObject sceneAreaObj = jsonObject.getAsJsonObject("sceneArea");
                areas.setSceneId("");
                areas.setSceneArea(new AABB(
                        sceneAreaObj.get("minX").getAsDouble(),
                        sceneAreaObj.get("minY").getAsDouble(),
                        sceneAreaObj.get("minZ").getAsDouble(),
                        sceneAreaObj.get("maxX").getAsDouble(),
                        sceneAreaObj.get("maxY").getAsDouble(),
                        sceneAreaObj.get("maxZ").getAsDouble()));
                if (!io.wifi.starrailexpress.scenery.SceneGeometry.isSectionAligned(areas.getSceneArea())) {
                    SRE.LOGGER.warn("sceneArea in map {} is not section-aligned; scene assets use expanded bounds {}",
                            mapName,
                            io.wifi.starrailexpress.scenery.SceneGeometry.expandedArea(areas.getSceneArea()));
                }
                areas.setSceneAreaConfigured(true);
                AreasWorldComponent.ScrollAxis fallbackAxis = AreasWorldComponent.ScrollAxis.X;
                if (jsonObject.has("sceneScroll")) {
                    String configuredAxis = jsonObject.get("sceneScroll").getAsString();
                    AreasWorldComponent.ScrollAxis axis = AreasWorldComponent.parseScrollAxis(configuredAxis, null);
                    if (axis == null) {
                        SRE.LOGGER.warn("Invalid sceneScroll '{}' in map {}, using X", configuredAxis, mapName);
                        axis = fallbackAxis;
                    }
                    areas.setSceneScroll(axis);
                } else {
                    areas.setSceneScroll(fallbackAxis);
                }
                areas.setSceneDisplayOffset(jsonObject.has("sceneDisplayOffset")
                        && jsonObject.get("sceneDisplayOffset").isJsonObject()
                                ? readVec3(jsonObject.getAsJsonObject("sceneDisplayOffset"))
                                : Vec3.ZERO);
                String assetHash = "";
                String remoteUrl = "";
                boolean trusted = false;
                if (jsonObject.has("sceneAsset") && jsonObject.get("sceneAsset").isJsonObject()) {
                    JsonObject sceneAsset = jsonObject.getAsJsonObject("sceneAsset");
                    if (sceneAsset.has("sha256")) {
                        assetHash = sceneAsset.get("sha256").getAsString().trim().toLowerCase(java.util.Locale.ROOT);
                        if (!io.wifi.starrailexpress.scenery.SceneAssetCodec.isValidHash(assetHash)) {
                            SRE.LOGGER.warn("Invalid scene asset hash in map {}", mapName);
                            assetHash = "";
                        }
                    }
                    if (sceneAsset.has("url")) {
                        remoteUrl = sceneAsset.get("url").getAsString().trim();
                    }
                    trusted = sceneAsset.has("trusted") && sceneAsset.get("trusted").getAsBoolean();
                }
                areas.setSceneAssetHash(assetHash);
                areas.setSceneAssetRemoteUrl(remoteUrl);
                areas.setSceneAssetTrusted(trusted);
                SRE.LOGGER.info("Loaded 'sceneArea': " + sceneAreaObj.get("minX").getAsDouble() + "," +
                        sceneAreaObj.get("minY").getAsDouble() + "," + sceneAreaObj.get("minZ").getAsDouble() + " to " +
                        sceneAreaObj.get("maxX").getAsDouble() + "," + sceneAreaObj.get("maxY").getAsDouble() + "," +
                        sceneAreaObj.get("maxZ").getAsDouble());
            } else {
                SceneLibrary.clearScene(areas);
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
            }

            areas.disabledRoles.clear();
            if (jsonObject.has("disabledRoles")) {
                var jsonArr = jsonObject.get("disabledRoles").getAsJsonArray();
                for (JsonElement data : jsonArr.asList()) {
                    areas.disabledRoles.add(data.getAsString());
                }
            }

            if (jsonObject.has("disabledModifiers")) {
                var jsonArr = jsonObject.get("disabledModifiers").getAsJsonArray();
                for (JsonElement data : jsonArr.asList()) {
                    areas.disabledModifiers.add(data.getAsString());
                }
            }
            areas.enableSceneTask.clear();
            if (jsonObject.has("enableSceneTask")) {
                var jsonArr = jsonObject.get("enableSceneTask").getAsJsonArray();
                for (JsonElement data : jsonArr.asList()) {
                    areas.enableSceneTask.add(data.getAsString());
                }
            }

            // 加载支持的游戏模式列表
            areas.gameModes.clear();
            if (jsonObject.has("gameModes")) {
                var jsonArr = jsonObject.get("gameModes").getAsJsonArray();
                for (JsonElement data : jsonArr.asList()) {
                    areas.gameModes.add(data.getAsString());
                }
                SRE.LOGGER.info("Loaded game modes for map " + mapName + ": " + areas.gameModes);
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
            io.wifi.starrailexpress.scenery.server.SceneAssetServer.activate(serverWorld);
            io.wifi.starrailexpress.scenery.network.SceneAssetNetwork.sendManifestToAll(serverWorld);
            last_start_map = mapName;

            SRE.LOGGER.info("Successfully loaded map: " + mapName);
            return true;
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to load map: " + mapName, e);
            return false;
        }
    }

    private static JsonObject sceneAssetJson(AreasWorldComponent areas, String hash) {
        JsonObject sceneAsset = new JsonObject();
        sceneAsset.addProperty("schema", SceneAsset.CURRENT_SCHEMA);
        if (hash != null && !hash.isBlank()) {
            sceneAsset.addProperty("sha256", hash);
        }
        if (!areas.getSceneAssetRemoteUrl().isBlank()) {
            sceneAsset.addProperty("url", areas.getSceneAssetRemoteUrl());
        }
        sceneAsset.addProperty("trusted", areas.isSceneAssetTrusted());
        return sceneAsset;
    }

    private static void setSceneReference(JsonObject root, String sceneId) {
        root.remove("sceneArea");
        root.remove("sceneScroll");
        root.remove("sceneDisplayOffset");
        root.remove("sceneAsset");
        if (sceneId == null || sceneId.isBlank()) {
            root.remove("scene");
        } else {
            root.addProperty("scene", sceneId.trim());
        }
    }

    private static JsonObject vec3Json(Vec3 value) {
        JsonObject result = new JsonObject();
        result.addProperty("x", value.x);
        result.addProperty("y", value.y);
        result.addProperty("z", value.z);
        return result;
    }

    private static Vec3 readVec3(JsonObject value) {
        return new Vec3(
                value.has("x") ? value.get("x").getAsDouble() : 0.0D,
                value.has("y") ? value.get("y").getAsDouble() : 0.0D,
                value.has("z") ? value.get("z").getAsDouble() : 0.0D);
    }

    public static ImportResult importMapConfig(ServerLevel serverWorld, String filename, String mapName,
            boolean overwrite) {
        try {
            String safeFilename = safeImportFilename(filename);
            Path importsDir = serverWorld.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("map_imports")
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(importsDir);
            Path source = importsDir.resolve(safeFilename).normalize();
            if (!source.startsWith(importsDir) || !Files.isRegularFile(source)) {
                return ImportResult.failure("导入文件不存在: map_imports/" + safeFilename);
            }
            long size = Files.size(source);
            if (size <= 0L || size > MAX_IMPORTED_MAP_BYTES) {
                return ImportResult.failure("地图配置必须小于 8 MiB 且不能为空");
            }

            JsonElement parsed;
            try (FileReader reader = new FileReader(source.toFile(), StandardCharsets.UTF_8)) {
                parsed = JsonParser.parseReader(reader);
            }
            if (!parsed.isJsonObject()) {
                return ImportResult.failure("地图配置根节点必须是 JSON 对象");
            }
            String validationError = validateImportedMapObject(parsed.getAsJsonObject());
            if (validationError != null) {
                return ImportResult.failure(validationError);
            }

            Path target = resolveMapConfigPath(serverWorld, mapName);
            if (Files.exists(target) && !overwrite) {
                return ImportResult.failure("目标地图已存在，请使用覆盖导入");
            }
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".import.tmp");
            Files.writeString(temp, prettyGson.toJson(parsed), StandardCharsets.UTF_8);
            atomicMove(temp, target);

            String normalizedName = normalizedMapName(mapName);
            boolean loaded = loadMap(serverWorld, normalizedName);
            return new ImportResult(true, loaded, loaded
                    ? "已导入并载入地图 " + normalizedName
                    : "配置已导入，但载入失败，请检查必需字段");
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to import map config {} as {}", filename, mapName, e);
            return ImportResult.failure("导入失败: " + e.getMessage());
        }
    }

    public static boolean isValidMapName(String mapName) {
        try {
            normalizedMapName(mapName);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Path resolveMapConfigPath(ServerLevel serverWorld, String mapName) throws IOException {
        Path mapsDir = serverWorld.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("train_maps")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(mapsDir);
        Path target = mapsDir.resolve(normalizedMapName(mapName) + ".json").normalize();
        if (!target.startsWith(mapsDir)) {
            throw new IOException("地图名称超出 train_maps 目录");
        }
        return target;
    }

    private static String normalizedMapName(String mapName) {
        if (mapName == null) {
            throw new IllegalArgumentException("地图名称不能为空");
        }
        String value = mapName.trim().replace('\\', '/');
        if (value.endsWith(".json")) {
            value = value.substring(0, value.length() - 5);
        }
        if (value.isBlank() || value.length() > 128 || value.startsWith("/") || value.endsWith("/")) {
            throw new IllegalArgumentException("地图名称为空、过长或格式无效");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment) || segment.contains(":")) {
                throw new IllegalArgumentException("地图名称包含不安全的路径片段");
            }
        }
        Path relative;
        try {
            relative = Path.of(value).normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("地图名称包含无效字符", e);
        }
        if (relative.isAbsolute() || relative.getNameCount() == 0) {
            throw new IllegalArgumentException("地图名称必须是相对路径");
        }
        for (Path part : relative) {
            String segment = part.toString();
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment) || segment.contains(":")) {
                throw new IllegalArgumentException("地图名称包含不安全的路径片段");
            }
        }
        return relative.toString().replace(File.separatorChar, '/');
    }

    private static String safeImportFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("导入文件名不能为空");
        }
        String value = filename.trim();
        if (!value.endsWith(".json")) {
            value += ".json";
        }
        Path name = Path.of(value).getFileName();
        if (!name.toString().equals(value) || value.contains("..")) {
            throw new IllegalArgumentException("导入文件名不安全");
        }
        return value;
    }

    private static String validateImportedMapObject(JsonObject root) {
        String[] requiredObjects = {
                "spawnPos", "spectatorSpawnPos", "readyArea", "playAreaOffset",
                "playArea", "resetTemplateArea", "resetPasteArea"
        };
        for (String key : requiredObjects) {
            if (!root.has(key) || !root.get(key).isJsonObject()) {
                return "地图配置缺少必需对象: " + key;
            }
        }
        return null;
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record ImportResult(boolean imported, boolean loaded, String message) {
        private static ImportResult failure(String message) {
            return new ImportResult(false, false, message);
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
        boolean allowN = SREConfig.instance().allowRandomChooseMapNotInMapConfig;
        availableMaps.removeIf(
                e -> {
                    final var first = MapConfig.getInstance().maps.stream().filter(mapEntry -> mapEntry.id.equals(e))
                            .findFirst();
                    if (first.isEmpty() && !allowN) {
                        // 不在地图里的地图是否参与随机
                        return true;
                    }
                    AtomicBoolean isNotAvailable = new AtomicBoolean(false);
                    first.ifPresent(
                            a -> {
                                isNotAvailable.set(!a.canSelect
                                        || (first.get().maxCount >= 0
                                                && serverWorld.players().size() > first.get().maxCount)
                                        || (first.get().minCount >= 0
                                                && serverWorld.players().size() < first.get().minCount));
                            });
                    return isNotAvailable.get();
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
     * 获取所有可用的地图列表（不含子文件夹）
     * 
     * @param serverWorld 服务器世界
     * @return 可用地图名称列表
     */
    public static List<String> getAvailableMaps(ServerLevel serverWorld) {
        return getAvailableMaps(serverWorld, false);
    }

    /**
     * 获取所有可用的地图列表
     * 
     * @param serverWorld 服务器世界
     * @param childFolder 是否枚举子文件夹（若为 true，则返回"子文件夹名/文件"格式）
     * @return 可用地图名称列表
     */
    public static List<String> getAvailableMaps(ServerLevel serverWorld, boolean childFolder) {
        List<String> maps = new ArrayList<>();

        try {
            Path mapsDirPath = Paths.get(serverWorld.getServer().getWorldPath(LevelResource.ROOT).toString(),
                    "train_maps");
            File mapsDir = mapsDirPath.toFile();

            if (mapsDir.exists() && mapsDir.isDirectory()) {
                if (childFolder) {
                    // 递归收集所有 .json 文件，并转换为相对路径（不含扩展名）
                    collectMapsRecursively(mapsDir.toPath(), mapsDir.toPath(), maps);
                } else {
                    // 只收集根目录下的 .json 文件
                    File[] files = mapsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                    if (files != null) {
                        for (File file : files) {
                            String fileName = file.getName();
                            String mapName = fileName.substring(0, fileName.length() - 5);
                            maps.add(mapName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to list available maps", e);
        }

        return maps;
    }

    /**
     * 递归遍历目录，收集所有 .json 文件的相对路径（不含扩展名）
     *
     * @param root 根目录（train_maps 的 Path）
     * @param dir  当前遍历的目录
     * @param maps 结果列表
     * @throws IOException 可能发生的 IO 异常（已在外部捕获）
     */
    private static void collectMapsRecursively(Path root, Path dir, List<String> maps) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    collectMapsRecursively(root, entry, maps);
                } else if (entry.toString().toLowerCase().endsWith(".json")) {
                    // 计算相对于 root 的路径，并移除 .json 后缀
                    Path relativePath = root.relativize(entry);
                    String pathStr = relativePath.toString();
                    // 移除最后的 .json
                    String mapName = pathStr.substring(0, pathStr.length() - 5);
                    // 统一使用正斜杠作为分隔符，保证跨平台一致性
                    maps.add(mapName.replace(File.separatorChar, '/'));
                }
            }
        }
    }
}
