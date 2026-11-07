package io.wifi.starrailexpress.client;

import me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SodiumOptionPolicyTest {
    @ParameterizedTest @ValueSource(strings = {"sodium:general.gamma", "sodium:quality.entity_distance", "sodium:quality.clouds", "sodium-extra:particles_all", "sodium-extra:particle.options.particles.minecraft.smoke", "sodium-extra:fog_start.options.dimensions.minecraft.overworld", "sodium-extra:cloud_fog.options.dimensions.minecraft.overworld", "sodium-extra:fog_shape", "sodium-extra:fog_shape.options.dimensions.minecraft.overworld", "sodium-extra:advanced_fog_settings", "sodium-extra:light_updates", "sodium-extra:piston", "sodium-extra:rain_snow", "sodium-extra:panini_projection", "sodium-extra:instant_sneak"})
    void gameplayVisibilityControlsAreLocked(String id) {
        assertTrue(SodiumOptionPolicy.isLocked(ResourceLocation.parse(id)));
    }

    @ParameterizedTest @ValueSource(strings = {"sodium:quality.weather", "sodium:quality.particles", "sodium:quality.leaves", "sodium:quality.ao", "sodium:quality.vignette", "sodium:general.render_distance", "sodium:general.framerate_limit", "sodium:general.fullscreen", "sodium:general.vsync", "sodium:performance.chunk_update_threads", "sodium:performance.use_entity_culling", "sodium-extra:show_fps", "sodium-extra:overlay_corner", "sodium-extra:toasts", "another_mod:particles_all"})
    void unrelatedAndPerformanceControlsAreNotLocked(String id) {
        assertFalse(SodiumOptionPolicy.isLocked(ResourceLocation.parse(id)));
    }

    @Test void restrictedBindingEnforcesLoadAndSave() {
        var stored = new AtomicReference<>(100);
        OptionBinding<Integer> binding = new OptionBinding<>() {
            public void save(Integer value) { stored.set(value); }
            public Integer load() { return stored.get(); }
        };
        var locked = SodiumOptionPolicy.binding(ResourceLocation.parse("sodium:general.gamma"), binding);
        assertEquals(0, locked.load());
        locked.save(99);
        assertEquals(0, stored.get());
        assertSame(binding, SodiumOptionPolicy.binding(ResourceLocation.parse("sodium:general.framerate_limit"), binding));
    }

    @Test void compositeLockedBindingNeverAcceptsEdits() {
        var stored = new AtomicReference<>("VANILLA");
        var binding = SodiumOptionPolicy.binding(ResourceLocation.parse("sodium-extra:fog_shape"), new OptionBinding<String>() {
            public void save(String value) { stored.set(value); }
            public String load() { return stored.get(); }
        });
        binding.save("NONE");
        assertEquals("VANILLA", binding.load());
    }

    @Test void sodiumConfigPreservesUserWeatherLeavesAndVignette() {
        var options = SodiumOptions.defaults();
        options.quality.weatherQuality = SodiumOptions.WeatherQuality.FAST;
        options.quality.leavesQuality = SodiumOptions.LeavesQuality.FAST;
        options.quality.enableVignette = false;
        var performance = options.performance;
        assertSame(options, SodiumOptionPolicy.enforce(options));
        assertEquals(SodiumOptions.WeatherQuality.FAST, options.quality.weatherQuality);
        assertEquals(SodiumOptions.LeavesQuality.FAST, options.quality.leavesQuality);
        assertFalse(options.quality.enableVignette);
        assertSame(performance, options.performance);
    }

    @Test void entityDistanceBindingRejectsProposalsAndReadsAlwaysOneHundredPercent() {
        var stored = new AtomicReference<>(500);
        var binding = SodiumOptionPolicy.binding(ResourceLocation.parse("sodium:quality.entity_distance"), new OptionBinding<Integer>() {
            public void save(Integer value) { stored.set(value); }
            public Integer load() { return stored.get(); }
        });
        assertEquals(100, binding.load());
        for (int value : new int[]{50, 125, 500, -1}) {
            binding.save(value);
            assertEquals(100, stored.get());
            stored.set(value); // 模拟配置重载或外部直接修改。
            assertEquals(100, binding.load());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"weather", "particles", "leaves", "ao", "vignette"})
    void unlockedQualityBindingsKeepOriginalReadAndWrite(String path) {
        var stored = new AtomicReference<>("before");
        OptionBinding<String> original = new OptionBinding<>() {
            public void save(String value) { stored.set(value); }
            public String load() { return stored.get(); }
        };
        var binding = SodiumOptionPolicy.binding(ResourceLocation.parse("sodium:quality." + path), original);
        assertSame(original, binding);
        binding.save("after");
        assertEquals("after", binding.load());
    }

    @Test void preventShadersIsHiddenAndLockedOffForBindingsAndRuntimeConfig() {
        var id = ResourceLocation.parse("sodium-extra:prevent_shaders");
        assertTrue(SodiumOptionPolicy.isLocked(id));
        assertTrue(SodiumOptionPolicy.isHidden(id));
        assertEquals(false, SodiumOptionPolicy.lockedValue(id));
        assertFalse(SodiumOptionPolicy.isHidden(ResourceLocation.parse("sodium-extra:show_fps")));
        assertFalse(SodiumOptionPolicy.isHidden(ResourceLocation.parse("another_mod:prevent_shaders")));
        var stored = new AtomicReference<>(true);
        var binding = SodiumOptionPolicy.binding(id, new OptionBinding<Boolean>() {
            public void save(Boolean value) { stored.set(value); }
            public Boolean load() { return stored.get(); }
        });
        assertFalse(binding.load());
        binding.save(true);
        assertFalse(stored.get());
        var options = new SodiumExtraGameOptions();
        options.extraSettings.preventShaders = true;
        SodiumExtraOptionPolicy.enforce(options);
        assertFalse(options.extraSettings.preventShaders);
    }

    @Test void extraRestoresEveryGameplayBooleanWithoutReplacingReferences() throws Exception {
        var options = new SodiumExtraGameOptions();
        Object[] enabledGroups = {options.animationSettings, options.particleSettings, options.detailSettings, options.renderSettings};
        for (Object group : enabledGroups) {
            for (var field : group.getClass().getFields()) {
                if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) field.setBoolean(group, false);
            }
        }
        options.renderSettings.limitBeaconBeamHeight = true;
        options.particleSettings.otherMap.put(ResourceLocation.parse("minecraft:smoke"), false);
        options.renderSettings.fogSettings.advanced = true;
        options.renderSettings.fogSettings.multiDimensionFogControl = true;
        options.renderSettings.fogSettings.atmospheric.distanceChunks = 32;
        options.renderSettings.fogSettings.protectedGameplay.enabledWhenAllowed = true;
        options.renderSettings.fogSettings.protectedGameplay.blindnessDistanceBlocks = 99;
        options.renderSettings.fogSettings.dimensionOverrides.put(ResourceLocation.parse("minecraft:overworld"), new SodiumExtraGameOptions.AtmosphericFogSettings());
        options.extraSettings.paniniProjection = options.extraSettings.cloudHeightOverride = options.extraSettings.instantSneak = true;
        options.extraSettings.showFps = true;
        assertSame(options, SodiumExtraOptionPolicy.enforce(options));
        for (Object group : enabledGroups) {
            for (var field : group.getClass().getFields()) {
                if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) {
                    assertEquals(!field.getName().equals("limitBeaconBeamHeight"), field.getBoolean(group), field.getName());
                }
            }
        }
        assertSame(enabledGroups[0], options.animationSettings);
        assertTrue(options.particleSettings.otherMap.isEmpty());
        assertFalse(options.renderSettings.fogSettings.advanced);
        assertFalse(options.renderSettings.fogSettings.multiDimensionFogControl);
        assertTrue(options.renderSettings.fogSettings.dimensionOverrides.isEmpty());
        assertEquals(0, options.renderSettings.fogSettings.atmospheric.distanceChunks);
        assertEquals(0, options.renderSettings.fogSettings.protectedGameplay.blindnessDistanceBlocks);
        assertFalse(options.renderSettings.fogSettings.protectedGameplay.enabledWhenAllowed);
        assertFalse(options.extraSettings.paniniProjection);
        assertFalse(options.extraSettings.cloudHeightOverride);
        assertFalse(options.extraSettings.instantSneak);
        assertTrue(options.extraSettings.showFps);
    }
}
