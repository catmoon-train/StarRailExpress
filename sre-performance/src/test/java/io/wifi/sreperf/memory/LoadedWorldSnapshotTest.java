package io.wifi.sreperf.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoadedWorldSnapshotTest {
    @Test void countsRegistryIdsNamespacesAndCacheCountWithoutModClasses() {
        Map<String, Object> world = LoadedWorldSnapshot.counts("minecraft:overworld", Map.of(
                "cobblemon:pokemon", 8L, "mega_showdown:effect", 2L, "minecraft:pig", 3L), 17);
        assertEquals(13L, world.get("loadedEntities"));
        assertEquals(17, world.get("loadedChunks"));
        assertEquals(8L, ((Map<?, ?>) world.get("entitiesByNamespace")).get("cobblemon"));
        assertEquals(2L, ((Map<?, ?>) world.get("entitiesByNamespace")).get("mega_showdown"));
        assertEquals(3, ((Map<?, ?>) world.get("entitiesByRegistryId")).size());
    }

    @Test void emptyWorldStillShowsTargetNamespacesAndMenuHasNoWorld() {
        Map<String, Object> world = LoadedWorldSnapshot.counts("minecraft:overworld", Map.of(), 0);
        assertEquals(0L, world.get("loadedEntities"));
        assertEquals(Map.of("cobblemon", 0L, "mega_showdown", 0L), world.get("entitiesByNamespace"));
        assertEquals(List.of(), LoadedWorldSnapshot.worlds("client menu", List.of()).get("worlds"));
    }
}
