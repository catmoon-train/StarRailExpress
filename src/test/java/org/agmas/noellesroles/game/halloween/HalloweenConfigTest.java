package org.agmas.noellesroles.game.halloween;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HalloweenConfigTest {
    private static final Gson GSON = new Gson();

    @Test void requiredDefaultsAreStable() throws Exception {
        HalloweenConfig config = new HalloweenConfig();
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("knockCooldownTicks", 300), Map.entry("knockUndeadTicks", 200),
                Map.entry("candyNpcLifetimeTicks", 600), Map.entry("undeadLifetimeTicks", 1800),
                Map.entry("maxEventUndead", 32), Map.entry("gunCooldownTicks", 200),
                Map.entry("gunFreezeTicks", 100), Map.entry("gunShieldSuppressionTicks", 600),
                Map.entry("sawChargeTicks", 10), Map.entry("heartDelayTicks", 300),
                Map.entry("candyDurationTicks", 600), Map.entry("costumeBagIntervalTicks", 2400),
                Map.entry("linkInviteTicks", 300), Map.entry("npcKillRadius", 8),
                Map.entry("pactReviveTicks", 1200), Map.entry("questTaskCount", 3),
                Map.entry("nightStartTicks", 2400), Map.entry("nightDurationTicks", 1200),
                Map.entry("nightIntervalTicks", 3600), Map.entry("nightDarkThreshold", 5));
        for (var entry : expected.entrySet()) assertEquals(entry.getValue(), HalloweenConfig.class.getField(entry.getKey()).getInt(config));
        assertEquals(10, config.linkDistance);
        assertEquals(0.2, config.candyNpcChance);
    }

    @Test void gsonPartialFilesKeepDefaultsAndCustomPoolsRoundTrip() {
        HalloweenConfig config = GSON.fromJson("{\"candyDurationTicks\":800,\"loot\":[{\"item\":\"minecraft:bread\",\"count\":3,\"weight\":7}],\"trades\":[]}", HalloweenConfig.class);
        List<String> warnings = new ArrayList<>();
        config.validate(id -> id.equals("minecraft:bread"), warnings::add);
        assertTrue(warnings.isEmpty());
        assertEquals(800, config.candyDurationTicks);
        assertEquals(2400, config.costumeBagIntervalTicks);
        HalloweenConfig copy = GSON.fromJson(GSON.toJson(config), HalloweenConfig.class);
        assertEquals(7, copy.loot.getFirst().weight);
        assertEquals(3, copy.loot.getFirst().count);
        assertTrue(copy.trades.isEmpty());
    }

    @Test void everyNegativeIntegerFallsBackAndNonFiniteDoublesAreRejected() throws Exception {
        HalloweenConfig config = new HalloweenConfig();
        HalloweenConfig defaults = new HalloweenConfig();
        for (Field field : HalloweenConfig.class.getFields()) if (field.getType() == int.class) field.setInt(config, -1);
        config.linkDistance = Double.NaN;
        config.candyNpcChance = Double.POSITIVE_INFINITY;
        List<String> warnings = new ArrayList<>();
        config.validate(id -> true, warnings::add);
        for (Field field : HalloweenConfig.class.getFields()) if (field.getType() == int.class) assertEquals(field.getInt(defaults), field.getInt(config), field.getName());
        assertEquals(10, config.linkDistance);
        assertEquals(0.2, config.candyNpcChance);
        assertTrue(warnings.size() >= 25);
    }

    @Test void unsafeZerosAndOverflowsFallBackButValidBoundaryValuesSurvive() {
        HalloweenConfig config = new HalloweenConfig();
        config.candyDurationTicks = 0;
        config.costumeBagIntervalTicks = Integer.MAX_VALUE;
        config.nightDarkThreshold = 16;
        config.linkDistance = -1;
        config.candyNpcChance = 1.01;
        config.nightStartTicks = 0;
        config.maxEventUndead = 0;
        config.validate(id -> true, ignored -> {});
        assertEquals(600, config.candyDurationTicks);
        assertEquals(2400, config.costumeBagIntervalTicks);
        assertEquals(5, config.nightDarkThreshold);
        assertEquals(10, config.linkDistance);
        assertEquals(0.2, config.candyNpcChance);
        assertEquals(0, config.nightStartTicks);
        assertEquals(0, config.maxEventUndead);
    }

    @Test void invalidIdsCountsAndWeightsAreRemovedWithWarnings() {
        HalloweenConfig config = new HalloweenConfig();
        config.loot = Arrays.asList(null, new HalloweenConfig.Loot("BAD ID", 1, 1),
                new HalloweenConfig.Loot("unknown:item", 1, 1), new HalloweenConfig.Loot("minecraft:bread", -1, 1),
                new HalloweenConfig.Loot("minecraft:bread", 1, 0), new HalloweenConfig.Loot("minecraft:bread", 65, 1),
                new HalloweenConfig.Loot("minecraft:bread", 1, Integer.MAX_VALUE), new HalloweenConfig.Loot("minecraft:bread", 2, 5));
        List<String> warnings = new ArrayList<>();
        config.validate(id -> !id.startsWith("unknown:"), warnings::add);
        assertEquals(1, config.loot.size());
        assertEquals(2, config.loot.getFirst().count);
        assertEquals(7, warnings.size());
    }

    @Test void emptyLootFallsBackWhileEmptyShopIsAllowedAndSoulContractsCannotBeSold() {
        HalloweenConfig config = new HalloweenConfig();
        config.loot = null;
        config.trades = Arrays.asList(null, new HalloweenConfig.Trade("noellesroles:halloween_soul_contract", 1, 1, 1),
                new HalloweenConfig.Trade("minecraft:bread", 1, -1, 2), new HalloweenConfig.Trade("minecraft:bread", 1, 0, 0),
                new HalloweenConfig.Trade("minecraft:bread", 1, 1, 0));
        config.validate(id -> true, ignored -> {});
        assertFalse(config.loot.isEmpty());
        assertEquals(1, config.trades.size());
        assertEquals("minecraft:bread", config.trades.getFirst().item);
        assertTrue(new HalloweenConfig().trades.stream().noneMatch(t -> t.item.endsWith("soul_contract")));
        config.trades = List.of();
        config.validate(id -> true, ignored -> {});
        assertTrue(config.trades.isEmpty());
    }

    @Test void weightedDrawUsesExactIntervalsAndLongTotals() {
        var a = new HalloweenConfig.Loot("minecraft:bread", 1, 3);
        var b = new HalloweenConfig.Loot("minecraft:apple", 1, 1);
        assertSame(a, HalloweenConfig.rollLoot(List.of(a, b), 0));
        assertSame(a, HalloweenConfig.rollLoot(List.of(a, b), 0.749999));
        assertSame(b, HalloweenConfig.rollLoot(List.of(a, b), 0.75));
        assertSame(b, HalloweenConfig.rollLoot(List.of(a, b), 1));
        a.weight = Integer.MAX_VALUE;
        b.weight = Integer.MAX_VALUE;
        assertSame(b, HalloweenConfig.rollLoot(List.of(a, b), 0.5));
        assertNull(HalloweenConfig.rollLoot(List.of(), 0));
    }

    @Test void defaultsUseActivityItemsAndReasonableFoodWithIndependentMutableLists() {
        HalloweenConfig first = new HalloweenConfig();
        HalloweenConfig second = new HalloweenConfig();
        assertTrue(first.loot.stream().anyMatch(l -> l.item.equals("noellesroles:halloween_blood_saw") && l.weight == 1));
        assertTrue(first.loot.stream().anyMatch(l -> l.item.equals("minecraft:bread")));
        first.loot.clear();
        first.trades.clear();
        assertFalse(second.loot.isEmpty());
        assertFalse(second.trades.isEmpty());
        second.validate(id -> true, warning -> fail(warning));
    }
}
