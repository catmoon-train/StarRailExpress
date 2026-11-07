package io.wifi.srepackcompat.startup;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SreModelLookupTest {
    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    @Test
    void realModelIdsKeepOriginalBaseSetAndNamespaceSelection() {
        var base = ModelResourceLocation.inventory(id("minecraft", "bow"));
        Set<ModelResourceLocation> models = new LinkedHashSet<>(Set.of(base));
        var source = Map.of("bow", models);
        SreModelLookup.rebuild(source, Map.of("bow", List.of("", "_pulling_0")));
        var selected = SreModelLookup.candidates(source, id("minecraft", "item/bow_pulling_0"), true);
        assertEquals(1, selected.size());
        assertSame(models, selected.iterator().next().getValue());
        assertSame(base, selected.iterator().next().getValue().iterator().next());
        assertTrue(SreModelLookup.candidates(source, id("other", "item/bow_pulling_0"), true).isEmpty());
        assertTrue(SreModelLookup.candidates(source, id("minecraft", "item/bow_unrelated"), true).isEmpty());
    }

    @Test
    void foreignSourceOrUnsupportedIndexUsesOriginalEntrySet() {
        var source = Map.of("key", Set.of(ModelResourceLocation.inventory(id("sre", "key"))));
        SreModelLookup.rebuild(source, Map.of());
        var foreign = new HashMap<>(source);
        assertSame(foreign.entrySet(), SreModelLookup.candidates(foreign, id("sre", "key"), false));
        Map<String, Set<ModelResourceLocation>> unsupported = new HashMap<>(); unsupported.put("bad", null);
        SreModelLookup.rebuild(unsupported, Map.of());
        assertSame(unsupported.entrySet(), SreModelLookup.candidates(unsupported, id("sre", "key"), false));
    }

    @Test
    void nextReloadRebuildsIdsAndStillWorksAfterStartupCountersStop() {
        var old = Map.of("key", Set.of(ModelResourceLocation.inventory(id("sre", "old"))));
        SreModelLookup.rebuild(old, Map.of());
        assertFalse(SreModelLookup.candidates(old, id("sre", "old"), false).isEmpty());
        SreModelLookup.finishStartup();
        var changed = Map.of("key", Set.of(ModelResourceLocation.inventory(id("sre", "new"))));
        SreModelLookup.rebuild(changed, Map.of("key", List.of("", "_ready")));
        assertTrue(SreModelLookup.candidates(changed, id("sre", "old"), false).isEmpty());
        assertFalse(SreModelLookup.candidates(changed, id("sre", "item/new_ready"), true).isEmpty());
        SreModelLookup.finishStartup();
    }
}
