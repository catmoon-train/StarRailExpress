package io.wifi.starrailexpress.client.resource;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DashModelCacheExclusionsTest {
    private ModelResourceLocation model(String id, String variant) {
        return new ModelResourceLocation(ResourceLocation.parse(id), variant);
    }

    @Test
    void matchesOnlyExactSupplementariesNamespace() {
        assertTrue(DashModelCacheExclusions.excluded(ResourceLocation.parse("supplementaries:blackboard")));
        assertFalse(DashModelCacheExclusions.excluded(ResourceLocation.parse("minecraft:supplementaries")));
        assertFalse(DashModelCacheExclusions.excluded(ResourceLocation.parse("supplementaries_extra:blackboard")));
        assertFalse(DashModelCacheExclusions.excluded(ResourceLocation.parse("amendments:wall_lantern")));
    }

    @Test
    void excludesBlockItemAndSpecialModelsWithoutMutatingSource() {
        var block = model("supplementaries:blackboard", "facing=north,waterlogged=false");
        var item = model("supplementaries:blackboard", "inventory");
        var special = model("supplementaries:special_models/block/blackboard_frame", "");
        var vanilla = model("minecraft:stone", "");
        var source = Map.of(block, 1, item, 2, special, 3, vanilla, 4);
        assertEquals(Map.of(vanilla, 4), DashModelCacheExclusions.filtered(source));
        assertEquals(4, source.size());
    }

    @Test
    void retainsOtherModelInstancesAndProvidesIndependentSnapshot() {
        var vanilla = model("minecraft:stone", "");
        Object baked = new Object();
        var source = new HashMap<>(Map.of(vanilla, baked));
        var result = DashModelCacheExclusions.filtered(source);
        assertSame(baked, result.get(vanilla));
        result.clear();
        assertSame(baked, source.get(vanilla));
    }

    @Test
    void legacyCachedModelsAreRemovedAndAllExcludedStatesReturnToFallback() {
        var block = model("supplementaries:blackboard", "facing=north");
        var otherState = model("supplementaries:blackboard", "facing=south");
        var item = model("supplementaries:blackboard", "inventory");
        var vanilla = model("minecraft:stone", "");
        var models = new HashMap<>(Map.of(block, 1, item, 2, vanilla, 3));
        var missing = new HashMap<>(Map.of("other-missing", vanilla));
        DashModelCacheExclusions.restoreFallback(models, missing,
                Map.of("north", block, "south", otherState));
        assertEquals(Map.of(vanilla, 3), models);
        assertEquals(Map.of("other-missing", vanilla, "north", block, "south", otherState), missing);
    }

    @Test
    void fallbackRestoreIsIdempotent() {
        var block = model("supplementaries:rope", "up=true");
        Map<ModelResourceLocation, Object> models = new HashMap<>();
        Map<String, ModelResourceLocation> missing = new HashMap<>();
        var states = Map.of("rope-up", block);
        DashModelCacheExclusions.restoreFallback(models, missing, states);
        DashModelCacheExclusions.restoreFallback(models, missing, states);
        assertEquals(states, missing);
        assertTrue(models.isEmpty());
    }

    @Test
    void emptyCacheAndAbsentSupplementariesAreNoOps() {
        assertTrue(DashModelCacheExclusions.filtered(Map.of()).isEmpty());
        var vanilla = model("minecraft:stone", "");
        var models = new HashMap<>(Map.of(vanilla, 1));
        Map<String, ModelResourceLocation> missing = new HashMap<>();
        DashModelCacheExclusions.restoreFallback(models, missing, Map.of());
        assertEquals(Map.of(vanilla, 1), models);
        assertTrue(missing.isEmpty());
    }
}
