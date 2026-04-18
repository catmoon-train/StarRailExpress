package io.wifi.starrailexpress.unlock;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 职业解锁数据存储器（服务器端）
 * - 记录全局游玩场次
 * - 记录被强制解锁的职业列表
 * - 数据持久化到 config/starrailexpress/role_unlock.json
 */
public class RoleUnlockStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoleUnlockStorage.class);
    private static final RoleUnlockStorage INSTANCE = new RoleUnlockStorage();
    private static final Gson GSON = new Gson();

    private final Path dataFile;

    private int globalGamesPlayed = 0;
    private final Set<String> forceUnlockedRoles = new HashSet<>();

    private RoleUnlockStorage() {
        this.dataFile = FabricLoader.getInstance()
                .getGameDir()
                .resolve("config")
                .resolve("starrailexpress")
                .resolve("role_unlock.json");
        load();
    }

    public static RoleUnlockStorage getInstance() {
        return INSTANCE;
    }

    public int getGlobalGamesPlayed() {
        return globalGamesPlayed;
    }

    public Set<String> getForceUnlockedRoles() {
        return Collections.unmodifiableSet(forceUnlockedRoles);
    }

    public boolean isForceUnlocked(ResourceLocation roleId) {
        return forceUnlockedRoles.contains(roleId.toString());
    }

    /** 每局游戏开始时调用，全局场次 +1 并保存 */
    public void incrementGlobalGames() {
        globalGamesPlayed++;
        save();
    }

    /** 强制解锁一批职业（用于"一键解锁"指令） */
    public void forceUnlockAll(Collection<ResourceLocation> roles) {
        for (ResourceLocation id : roles) {
            forceUnlockedRoles.add(id.toString());
        }
        save();
    }

    /** 重置强制解锁列表（管理员可选操作） */
    public void clearForceUnlocks() {
        forceUnlockedRoles.clear();
        save();
    }

    // ─── 文件 I/O ────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(dataFile)) {
            applyConfiguredDefaultUnlocks();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;
            if (obj.has("globalGamesPlayed")) {
                globalGamesPlayed = obj.get("globalGamesPlayed").getAsInt();
            }
            if (obj.has("forceUnlockedRoles")) {
                JsonArray arr = obj.getAsJsonArray("forceUnlockedRoles");
                for (JsonElement el : arr) {
                    forceUnlockedRoles.add(el.getAsString());
                }
            }
            LOGGER.info("[RoleUnlock] 已加载解锁数据：全局场次={}, 强制解锁={}",
                    globalGamesPlayed, forceUnlockedRoles.size());
            if (applyConfiguredDefaultUnlocks()) {
                save();
            }
        } catch (Exception e) {
            LOGGER.error("[RoleUnlock] 读取 role_unlock.json 失败", e);
        }
    }

    private boolean applyConfiguredDefaultUnlocks() {
        boolean changed = false;
        try {
            var cfg = StupidExpressConfig.getInstance().rolesSection.roleUnlockSection;
            if (!cfg.enableRoleUnlockSystem || !cfg.unlockBasicRolesAtStart) {
                return false;
            }
            for (String id : cfg.basicDefaultUnlockedRoles) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && forceUnlockedRoles.add(rl.toString())) {
                    changed = true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[RoleUnlock] 读取默认基础职业解锁配置失败，使用当前存档数据", e);
        }
        return changed;
    }

    private void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("globalGamesPlayed", globalGamesPlayed);
            JsonArray arr = new JsonArray();
            for (String id : forceUnlockedRoles) {
                arr.add(id);
            }
            obj.add("forceUnlockedRoles", arr);
            try (Writer writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[RoleUnlock] 保存 role_unlock.json 失败", e);
        }
    }
}
