package io.wifi.srepokemoncompat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/** 迁移原宝可梦 Java 声明覆盖；不会覆盖其他模组的依赖规则。 */
public final class PokemonConfigInstaller implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        var loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("cobblemon")) return;
        try {
            install(loader.getModContainer("sre_pokemon_compat").orElseThrow().getRootPaths(), loader.getConfigDir());
        } catch (IOException error) {
            throw new IllegalStateException("无法合并宝可梦依赖覆盖，原文件已保留", error);
        }
    }

    public static int install(List<Path> roots, Path configDir) throws IOException {
        Path target = configDir.toAbsolutePath().normalize().resolve("fabric_loader_dependencies.json");
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("拒绝通过符号链接覆盖配置: " + path);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target)) {
            throw new IOException("依赖覆盖目标不是普通文件: " + target);
        }
        for (Path root : roots) {
            Path source = root.resolve("config/fabric_loader_dependencies.json");
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("内置覆盖不是普通文件");
            byte[] content = Files.readAllBytes(source);
            if (Files.isRegularFile(target)) {
                content = merge(content, Files.readString(target, StandardCharsets.UTF_8));
                if (Arrays.equals(content, Files.readAllBytes(target))) return 0;
            }
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".sre-pokemon-compat-", ".tmp");
            try {
                Files.write(temp, content);
                try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException error) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
            return 1;
        }
        return 0;
    }

    private static byte[] merge(byte[] bundled, String existing) throws IOException {
        try {
            var original = JsonParser.parseString(existing).getAsJsonObject();
            if (original.get("version").getAsInt() != 1) throw new IllegalArgumentException("未知覆盖配置版本");
            var defaults = JsonParser.parseString(new String(bundled, StandardCharsets.UTF_8)).getAsJsonObject();
            var overrides = objectOrCreate(original, "overrides");
            var cobblemon = objectOrCreate(overrides, "cobblemon");
            if (cobblemon.has("depends")) throw new IllegalArgumentException("Cobblemon 已有全量 depends 覆盖，请先手动合并");
            var depends = objectOrCreate(cobblemon, "+depends");
            var javaVersions = defaults.getAsJsonObject("overrides").getAsJsonObject("cobblemon")
                    .getAsJsonObject("+depends").get("java");
            var remove = objectOrCreate(cobblemon, "-depends");
            if (javaVersions.equals(depends.get("java")) && remove.has("java")) return existing.getBytes(StandardCharsets.UTF_8);
            remove.addProperty("java", "*");
            depends.add("java", javaVersions.deepCopy());
            return (new GsonBuilder().setPrettyPrinting().create().toJson(original) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException error) { throw new IOException("无法合并 fabric_loader_dependencies.json，保留原文件", error); }
    }

    private static JsonObject objectOrCreate(JsonObject parent, String key) {
        if (!parent.has(key)) parent.add(key, new JsonObject());
        return parent.getAsJsonObject(key);
    }
}
