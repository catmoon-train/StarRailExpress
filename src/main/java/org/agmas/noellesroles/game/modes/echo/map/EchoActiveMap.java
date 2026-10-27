package org.agmas.noellesroles.game.modes.echo.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 当前世界生效的回声地图绑定；没有绑定时按 Areas 地图名自动匹配侧文件。
 *
 * <p>与 {@link EchoRuleRepository} 同一套底线：拒绝符号链接、原子写入、坏文件不静默回退。
 * 绑定只记录 mapId，侧文件本体仍在 {@code echo_freight/maps/} 下，由地图编辑器维护。
 */
public final class EchoActiveMap {
    public record Binding(String mapId,String boundBy,long boundAt) { }
    private static final int MAX_BYTES = 4096;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EchoActiveMap() { }

    private static Path file(Path worldRoot) throws IOException {
        Path root = worldRoot.toRealPath(), dir = root.resolve("echo_freight"), path = dir.resolve("active.json");
        if (Files.isSymbolicLink(dir) || Files.isSymbolicLink(path)) throw new IOException("绑定路径不得包含符号链接");
        return path;
    }

    public static Binding get(Path worldRoot) throws IOException {
        Path path = file(worldRoot);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("绑定目标不是普通文件");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("绑定文件超过4KiB");
        String json = new String(bytes, StandardCharsets.UTF_8);
        if (json.startsWith("\uFEFF")) throw new IOException("绑定文件含BOM");
        try {
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null || !object.has("mapId")) throw new IOException("绑定缺少mapId");
            var id = object.get("mapId");
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) throw new IOException("绑定mapId必须是字符串");
            String mapId = id.getAsString();
            EchoMapRepository.requireMapId(mapId);
            String boundBy = object.has("boundBy") && object.get("boundBy").isJsonPrimitive() ? object.get("boundBy").getAsString() : "";
            long boundAt = object.has("boundAt") && object.get("boundAt").isJsonPrimitive() && object.get("boundAt").getAsJsonPrimitive().isNumber()
                    ? object.get("boundAt").getAsLong() : 0L;
            return new Binding(mapId, boundBy, boundAt);
        } catch (RuntimeException invalid) { throw new IOException("绑定文件无效: " + invalid.getMessage(), invalid); }
    }

    /** 生效 mapId：有绑定用绑定，否则回退 Areas 地图名（调用方保证回退值可用）。 */
    public static String effective(Path worldRoot, String fallback) throws IOException {
        Binding binding = get(worldRoot);
        return binding == null ? fallback : binding.mapId();
    }

    public static Binding set(Path worldRoot, String mapId, String boundBy) throws IOException {
        EchoMapRepository.requireMapId(mapId);
        Binding binding = new Binding(mapId, boundBy == null ? "" : boundBy, System.currentTimeMillis());
        Path path = file(worldRoot);
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".echo-active-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(binding) + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return binding;
    }

    public static boolean clear(Path worldRoot) throws IOException {
        return Files.deleteIfExists(file(worldRoot));
    }
}
