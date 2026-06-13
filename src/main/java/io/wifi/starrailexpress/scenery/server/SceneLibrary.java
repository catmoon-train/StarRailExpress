package io.wifi.starrailexpress.scenery.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.scenery.SceneAsset;
import io.wifi.starrailexpress.scenery.SceneAssetCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SceneLibrary {
    private static final int SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SceneLibrary() {
    }

    public static Result saveCurrent(ServerLevel level, String requestedId, boolean overwrite) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (!areas.isSceneAreaConfigured()) {
            return Result.failure("请先设置场景源区域");
        }
        try {
            String id = normalizeId(requestedId);
            Path path = scenePath(level, id);
            if (Files.exists(path) && !overwrite) {
                return Result.failure("场景已存在，请使用覆盖保存");
            }
            writeDefinition(path, id, areas);
            areas.setSceneId(id);
            areas.sync();
            return Result.success(id, "已保存场景 " + id);
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to save scene {}", requestedId, e);
            return Result.failure("保存场景失败: " + e.getMessage());
        }
    }

    public static Result updateCurrent(ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas.getSceneId().isBlank()) {
            return Result.failure("当前地图没有指定场景 ID");
        }
        return saveCurrent(level, areas.getSceneId(), true);
    }

    public static Result loadInto(ServerLevel level, String requestedId) {
        try {
            String id = normalizeId(requestedId);
            Path path = scenePath(level, id);
            if (!Files.isRegularFile(path)) {
                return Result.failure("场景不存在: " + id);
            }
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            apply(level, id, root);
            return Result.success(id, "已载入场景 " + id);
        } catch (Exception e) {
            SRE.LOGGER.error("Failed to load scene {}", requestedId, e);
            return Result.failure("载入场景失败: " + e.getMessage());
        }
    }

    public static Result delete(ServerLevel level, String requestedId) {
        try {
            String id = normalizeId(requestedId);
            AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
            if (id.equals(areas.getSceneId())) {
                return Result.failure("不能删除当前地图正在使用的场景");
            }
            return Files.deleteIfExists(scenePath(level, id))
                    ? Result.success(id, "已删除场景 " + id)
                    : Result.failure("场景不存在: " + id);
        } catch (Exception e) {
            return Result.failure("删除场景失败: " + e.getMessage());
        }
    }

    public static List<String> list(ServerLevel level) {
        List<String> result = new ArrayList<>();
        Path directory = directory(level);
        try {
            Files.createDirectories(directory);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
                for (Path path : stream) {
                    String filename = path.getFileName().toString();
                    result.add(filename.substring(0, filename.length() - 5));
                }
            }
        } catch (IOException e) {
            SRE.LOGGER.warn("Unable to list scene library", e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static boolean isValidId(String value) {
        try {
            normalizeId(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static void clearScene(AreasWorldComponent areas) {
        areas.setSceneId("");
        areas.setSceneArea(areas.getPlayArea());
        areas.setSceneAreaConfigured(false);
        areas.setSceneScroll(AreasWorldComponent.ScrollAxis.NONE);
        areas.setSceneDisplayOffset(Vec3.ZERO);
        areas.setSceneAssetHash("");
        areas.setSceneAssetRemoteUrl("");
        areas.setSceneAssetTrusted(false);
    }

    private static void apply(ServerLevel level, String id, JsonObject root) throws IOException {
        if (!root.has("sourceArea") || !root.get("sourceArea").isJsonObject()) {
            throw new IOException("场景缺少 sourceArea");
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        areas.setSceneId(id);
        areas.setSceneArea(readBox(root.getAsJsonObject("sourceArea")));
        areas.setSceneAreaConfigured(true);
        areas.setSceneScroll(AreasWorldComponent.parseScrollAxis(
                root.has("scroll") ? root.get("scroll").getAsString() : "",
                AreasWorldComponent.ScrollAxis.X));
        areas.setSceneDisplayOffset(root.has("displayOffset") && root.get("displayOffset").isJsonObject()
                ? readVec3(root.getAsJsonObject("displayOffset"))
                : Vec3.ZERO);

        String hash = "";
        String remoteUrl = "";
        boolean trusted = false;
        if (root.has("asset") && root.get("asset").isJsonObject()) {
            JsonObject asset = root.getAsJsonObject("asset");
            if (asset.has("sha256")) {
                String candidate = asset.get("sha256").getAsString().trim().toLowerCase(Locale.ROOT);
                if (SceneAssetCodec.isValidHash(candidate)) {
                    hash = candidate;
                }
            }
            remoteUrl = asset.has("url") ? asset.get("url").getAsString().trim() : "";
            trusted = asset.has("trusted") && asset.get("trusted").getAsBoolean();
        }
        areas.setSceneAssetHash(hash);
        areas.setSceneAssetRemoteUrl(remoteUrl);
        areas.setSceneAssetTrusted(trusted);
    }

    private static void writeDefinition(Path path, String id, AreasWorldComponent areas) throws IOException {
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("id", id);
        root.add("sourceArea", boxJson(areas.getSceneArea()));
        root.addProperty("scroll", areas.getSceneScroll().name());
        root.add("displayOffset", vec3Json(areas.getSceneDisplayOffset()));

        JsonObject asset = new JsonObject();
        asset.addProperty("schema", SceneAsset.CURRENT_SCHEMA);
        if (!areas.getSceneAssetHash().isBlank()) {
            asset.addProperty("sha256", areas.getSceneAssetHash());
        }
        if (!areas.getSceneAssetRemoteUrl().isBlank()) {
            asset.addProperty("url", areas.getSceneAssetRemoteUrl());
        }
        asset.addProperty("trusted", areas.isSceneAssetTrusted());
        root.add("asset", asset);

        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        atomicMove(temp, path);
    }

    private static Path directory(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("scene_library")
                .toAbsolutePath()
                .normalize();
    }

    private static Path scenePath(ServerLevel level, String id) throws IOException {
        Path directory = directory(level);
        Files.createDirectories(directory);
        Path path = directory.resolve(normalizeId(id) + ".json").normalize();
        if (!path.startsWith(directory)) {
            throw new IOException("场景 ID 超出 scene_library 目录");
        }
        return path;
    }

    private static String normalizeId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("场景 ID 不能为空");
        }
        String id = value.trim();
        if (id.endsWith(".json")) {
            id = id.substring(0, id.length() - 5);
        }
        if (id.isBlank() || id.length() > 128 || id.contains("..") || id.contains(":")
                || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("场景 ID 无效");
        }
        return id;
    }

    private static JsonObject boxJson(AABB box) {
        JsonObject value = new JsonObject();
        value.addProperty("minX", box.minX);
        value.addProperty("minY", box.minY);
        value.addProperty("minZ", box.minZ);
        value.addProperty("maxX", box.maxX);
        value.addProperty("maxY", box.maxY);
        value.addProperty("maxZ", box.maxZ);
        return value;
    }

    private static AABB readBox(JsonObject value) {
        return new AABB(
                value.get("minX").getAsDouble(),
                value.get("minY").getAsDouble(),
                value.get("minZ").getAsDouble(),
                value.get("maxX").getAsDouble(),
                value.get("maxY").getAsDouble(),
                value.get("maxZ").getAsDouble());
    }

    private static JsonObject vec3Json(Vec3 vec) {
        JsonObject value = new JsonObject();
        value.addProperty("x", vec.x);
        value.addProperty("y", vec.y);
        value.addProperty("z", vec.z);
        return value;
    }

    private static Vec3 readVec3(JsonObject value) {
        return new Vec3(
                value.has("x") ? value.get("x").getAsDouble() : 0.0D,
                value.has("y") ? value.get("y").getAsDouble() : 0.0D,
                value.has("z") ? value.get("z").getAsDouble() : 0.0D);
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Result(boolean success, String id, String message) {
        public static Result success(String id, String message) {
            return new Result(true, id, message);
        }

        public static Result failure(String message) {
            return new Result(false, "", message);
        }
    }
}
