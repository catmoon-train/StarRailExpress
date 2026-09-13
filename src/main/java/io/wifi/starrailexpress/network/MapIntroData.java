package io.wifi.starrailexpress.network;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.config.NoellesRolesConfig;

/**
 * 地图介绍 / 轮抽 / 投票界面用的展示数据（{@link MapDisplayInfo}）的服务端来源。
 *
 * <p>
 * 两点约定：
 * <ul>
 * <li><b>只收集投票配置（{@code <world>/train_vote_maps.json}）里登记过的地图</b>；
 * 管理员请求 {@code includeAll} 时才临时收集磁盘上的全部地图（临时构建、不写缓存）。</li>
 * <li><b>缓存在每次 {@link MapManager#loadMap} 时重建</b>（每局一次），
 * 平时请求直接读缓存，不再每次请求都重新读盘/解析。</li>
 * </ul>
 *
 * <p>
 * 地图特性 = 地图自身 {@code AreasSettings.customMapFeatures} ∪ 旧的 NoellesRolesConfig 地图列表
 * ——旧配置在这里被兼容成「地图特性」，因此不再单独把 config 同步给客户端。
 */
public final class MapIntroData {

    /** 只含投票配置里的地图；未构建过时为 null */
    private static volatile List<MapDisplayInfo> cache;

    private MapIntroData() {
    }

    /** 每次 loadMap 时调用（本局唯一权威刷新点）；任何异常都不允许影响地图加载。 */
    public static void rebuild(MinecraftServer server, String loadedMapId) {
        if (server == null) {
            return;
        }
        try {
            List<MapDisplayInfo> collected = collect(server, false);
            cache = collected;
            SRE.LOGGER.info("[MapIntro] display cache rebuilt: {} maps (current map: {})", collected.size(),
                    loadedMapId == null ? "-" : loadedMapId);
        } catch (Exception e) {
            SRE.LOGGER.warn("[MapIntro] failed to rebuild display cache", e);
        }
    }

    /**
     * 取展示数据。
     *
     * @param includeAll 管理员视图：临时收集磁盘上全部地图（不写缓存）
     */
    public static List<MapDisplayInfo> get(MinecraftServer server, boolean includeAll) {
        if (server == null) {
            return List.of();
        }
        if (includeAll) {
            return collect(server, true);
        }
        List<MapDisplayInfo> local = cache;
        if (local == null) {
            // 还没加载过任何地图（缓存未构建）时惰性构建一次
            rebuild(server, null);
            local = cache;
        }
        return local == null ? List.of() : local;
    }

    /** 轮抽开关：就地更新缓存里这一张图的 canSelect，避免同一局内显示过期。 */
    public static void updateCanSelect(String mapId, boolean canSelect) {
        List<MapDisplayInfo> local = cache;
        if (local == null || mapId == null) {
            return;
        }
        List<MapDisplayInfo> updated = new ArrayList<>(local.size());
        for (MapDisplayInfo info : local) {
            updated.add(info.id().equals(mapId) ? info.withCanSelect(canSelect) : info);
        }
        cache = List.copyOf(updated);
    }

    /** 强制失效（例如投票配置重载后想立刻刷新）。 */
    public static void invalidate() {
        cache = null;
    }

    // ==================== 收集 ====================

    private static List<MapDisplayInfo> collect(MinecraftServer server, boolean includeAll) {
        ServerLevel overworld = server.overworld();
        Map<String, MapConfig.MapEntry> voteEntries = new LinkedHashMap<>();
        for (MapConfig.MapEntry entry : ServerMapConfig.getInstance(server).getMaps()) {
            if (entry != null && entry.id != null) {
                voteEntries.put(entry.id, entry);
            }
        }

        List<String> ids = new ArrayList<>();
        if (includeAll) {
            ids.addAll(MapManager.getAvailableMaps(overworld, true));
        } else {
            ids.addAll(voteEntries.keySet());
        }

        Path mapsDir = server.getWorldPath(LevelResource.ROOT).resolve("train_maps");
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        List<MapDisplayInfo> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            Path path = mapsDir.resolve(id + ".json").normalize();
            if (!Files.isRegularFile(path)) {
                SRE.LOGGER.warn("[MapIntro] map config not found, skipped: {}", id);
                continue;
            }
            JsonObject root;
            try {
                root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                SRE.LOGGER.warn("[MapIntro] failed to read map config: {}", id, e);
                continue;
            }
            result.add(build(id, root, voteEntries.get(id), config));
        }
        return List.copyOf(result);
    }

    private static MapDisplayInfo build(String id, JsonObject root, MapConfig.MapEntry voteEntry,
            NoellesRolesConfig config) {
        AreasSettings settings = MapManager.parseAreasSettings(root);
        boolean hasVoteConfig = voteEntry != null;
        return new MapDisplayInfo(
                id,
                hasVoteConfig && voteEntry.displayName != null ? voteEntry.displayName : "",
                hasVoteConfig && voteEntry.description != null ? voteEntry.description : "",
                hasVoteConfig ? voteEntry.getColor() : 0,
                featureNames(settings, id, config),
                hasVoteConfig,
                hasVoteConfig ? voteEntry.minCount : -1,
                hasVoteConfig ? voteEntry.maxCount : -1,
                hasVoteConfig && voteEntry.canSelect,
                hasVoteConfig && voteEntry.gameModes != null ? List.copyOf(voteEntry.gameModes) : List.of(),
                intValue(root, "roomCount", 1),
                stringArray(root, "disabledTasks"),
                stringArray(root, "disabledRoles"),
                stringArray(root, "enableSceneTask"),
                settings.minigameQuestEnabled,
                settings.meetingEnabled,
                settings.meetingVoteEnabled,
                settings.bellMeetingEnabled,
                settings.mapStatusBar == null ? "NONE" : settings.mapStatusBar.name(),
                settings.canJump,
                settings.canSimpleSwim,
                settings.canUnderWater,
                settings.allowInDeepWater,
                settings.canSwim,
                settings.enableOxygenDrowning,
                settings.snowEnabled,
                settings.sandEnabled,
                settings.planeCrashEventEnabled,
                settings.fogEnabled,
                settings.fogEnd,
                settings.weather == null ? "clear" : settings.weather.name(),
                settings.gravityModifier,
                settings.time,
                settings.daylightCycle,
                settings.weatherCycle,
                settings.mobEffects == null ? List.of() : List.copyOf(settings.mobEffects),
                settings.initialItems == null ? List.of() : List.copyOf(settings.initialItems));
    }

    /**
     * 地图特性（枚举名列表）：地图自己声明的 {@code customMapFeatures} 为主，
     * 旧的 NoellesRolesConfig 列表（奇遇村/大图/水下/天空/机关/骑马/实验室）作为兼容来源一并并入。
     */
    private static List<String> featureNames(AreasSettings settings, String mapId, NoellesRolesConfig config) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (settings.customMapFeatures != null) {
            for (MapSpecialFeatures feature : settings.customMapFeatures) {
                if (feature != null) {
                    names.add(feature.name());
                }
            }
        }
        if (config != null) {
            addFeature(names, MapSpecialFeatures.QIYUCUN, config.maChenXuMaps, mapId);
            addFeature(names, MapSpecialFeatures.BIGMAP, config.swastMaps, mapId);
            addFeature(names, MapSpecialFeatures.UNDERWATER, config.underwaterRolesMaps, mapId);
            addFeature(names, MapSpecialFeatures.FLY, config.airRolesMaps, mapId);
            addFeature(names, MapSpecialFeatures.TRAP, config.trapRolesMaps, mapId);
            addFeature(names, MapSpecialFeatures.HORSE, config.horseRolesMaps, mapId);
            addFeature(names, MapSpecialFeatures.LAB, config.labRolesMaps, mapId);
        }
        return List.copyOf(names);
    }

    private static void addFeature(LinkedHashSet<String> names, MapSpecialFeatures feature, List<String> maps,
            String mapId) {
        if (contains(maps, mapId)) {
            names.add(feature.name());
        }
    }

    private static boolean contains(List<String> list, String mapId) {
        return list != null && mapId != null && list.contains(mapId);
    }

    private static int intValue(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) {
            return fallback;
        }
        try {
            return root.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<String> stringArray(JsonObject root, String key) {
        if (root == null || !root.has(key)) {
            return List.of();
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child != null && child.isJsonPrimitive()) {
                out.add(child.getAsString());
            }
        }
        return List.copyOf(out);
    }
}
