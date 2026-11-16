package io.wifi.sreperf.memory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 必须在所属游戏线程调用；只读取实体索引与区块缓存计数，不查询或加载区块。 */
public final class LoadedWorldSnapshot {
    private LoadedWorldSnapshot() { }

    public static Map<String, Object> server(MinecraftServer server) {
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (var level : server.getAllLevels()) {
            worlds.add(world(level.dimension().location().toString(), level.getAllEntities(),
                    level.getChunkSource().getLoadedChunksCount()));
        }
        return worlds("server: all currently loaded dimensions", worlds);
    }

    public static Map<String, Object> world(String dimension, Iterable<? extends Entity> entities, int loadedChunks) {
        Map<String, Long> counts = new TreeMap<>();
        for (Entity entity : entities) {
            counts.merge(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), 1L, Long::sum);
        }
        return counts(dimension, counts, loadedChunks);
    }

    static Map<String, Object> counts(String dimension, Map<String, Long> counts, int loadedChunks) {
        Map<String, Long> namespaces = new TreeMap<>();
        counts.forEach((id, count) -> namespaces.merge(id.substring(0, id.indexOf(':')), count, Long::sum));
        // 即使未安装或当前没有实体，也显式显示目标命名空间；不假装它们覆盖了宝可梦的全部数据。
        namespaces.putIfAbsent("cobblemon", 0L);
        namespaces.putIfAbsent("mega_showdown", 0L);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dimension", dimension);
        out.put("loadedChunks", loadedChunks);
        out.put("loadedEntities", counts.values().stream().mapToLong(Long::longValue).sum());
        out.put("entitiesByRegistryId", new TreeMap<>(counts));
        out.put("entitiesByNamespace", namespaces);
        return out;
    }

    public static Map<String, Object> worlds(String scope, List<Map<String, Object>> worlds) {
        return Map.of("capturedAt", Instant.now().toString(), "scope", scope, "worlds", List.copyOf(worlds),
                "measurement", "Loaded entity index only (not saved/unloaded entities, party/PC data, block entities or heap objects). ChunkSource loaded-cache count, not ticking/visible/rendered chunks. No chunk lookup or generation. Counts are not memory bytes.");
    }
}
