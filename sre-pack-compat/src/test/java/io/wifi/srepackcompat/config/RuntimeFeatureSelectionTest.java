package io.wifi.srepackcompat.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeFeatureSelectionTest {
    private static final List<String> MIXINS = List.of("FramePacerMixin", "TextureUrlMixin",
            "TexturePropertyMixin", "TextureServicesKeyMixin", "RenderTargetLifetimeMixin");

    @Test
    void standalonePackageKeepsAllBuiltInFeatures() {
        for (String mixin : MIXINS) assertTrue(RuntimeFeatureSelection.shouldApply(mixin, id -> false));
    }

    @Test
    void fullOriginalModSetDisablesEveryOverlappingMixin() {
        Set<String> mods = Set.of("framepacer", "skin-unlocker", "gpumemleakfix");
        for (String mixin : MIXINS) assertFalse(RuntimeFeatureSelection.shouldApply(
                "io.wifi.srepackcompat.mixin.runtime." + mixin, mods::contains));
    }

    @Test
    void onlyMatchingExternalImplementationDisablesBuiltInFeature() {
        for (String mixin : MIXINS) {
            assertEquals(mixin.equals("FramePacerMixin") ? false : true,
                    RuntimeFeatureSelection.shouldApply(mixin, "framepacer"::equals));
            assertEquals(!mixin.startsWith("Texture"),
                    RuntimeFeatureSelection.shouldApply(mixin, "skin-unlocker"::equals));
            assertEquals(!mixin.equals("RenderTargetLifetimeMixin"),
                    RuntimeFeatureSelection.shouldApply(mixin, "gpumemleakfix"::equals));
        }
    }

    @Test
    void unrelatedMixinsAndModIdsAreNotDisabled() {
        assertTrue(RuntimeFeatureSelection.shouldApply("FutureMixin", id -> true));
        for (String mixin : MIXINS) assertTrue(RuntimeFeatureSelection.shouldApply(mixin, "unrelated"::equals));
    }

    @Test
    void metadataAllowsOriginalModsAndRegistersTheGuard() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/fabric.mod.json"), StandardCharsets.UTF_8)) {
            var breaks = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("breaks");
            for (String mod : List.of("framepacer", "skin-unlocker", "gpumemleakfix", "susinstantswap"))
                assertFalse(breaks.has(mod), "当前元数据不得重新引入已移除的硬冲突: " + mod);
        }
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/sre-pack-compat-runtime.mixins.json"), StandardCharsets.UTF_8)) {
            var config = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals("io.wifi.srepackcompat.SrePackCompatMixinPlugin", config.get("plugin").getAsString());
            assertEquals(MIXINS, config.getAsJsonArray("client").asList().stream().map(e -> e.getAsString()).toList());
        }
    }
}
