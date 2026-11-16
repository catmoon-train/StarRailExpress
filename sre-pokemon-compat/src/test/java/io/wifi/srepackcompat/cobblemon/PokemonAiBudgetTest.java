package io.wifi.srepackcompat.cobblemon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PokemonAiBudgetTest {
    @Test void everySlidingTenTicksRetainExactlyThreeThoughts() {
        for (int id : new int[]{0, 1, 9, 10, 691, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (long start = -20; start < 100; start++) {
                int retained = 0;
                for (int tick = 0; tick < 10; tick++) {
                    if (PokemonAiBudget.shouldThink(start + tick, id)) retained++;
                }
                assertEquals(3, retained, "entity=" + id + ", start=" + start);
            }
        }
    }

    @Test void balancedEntityIdsSpreadWorkAcrossEveryTick() {
        for (long tick = 0; tick < 20; tick++) {
            int retained = 0;
            for (int id = 0; id < 1000; id++) {
                if (PokemonAiBudget.shouldThink(tick, id)) retained++;
            }
            assertEquals(300, retained);
        }
    }

    @Test void intervalsAreThreeThreeAndFourRatherThanOneLargeBurst() {
        var ticks = new ArrayList<Integer>();
        for (int tick = 0; tick < 21; tick++) {
            if (PokemonAiBudget.shouldThink(tick, 0)) ticks.add(tick);
        }
        assertEquals(List.of(0, 3, 6, 10, 13, 16, 20), ticks);
    }

    @Test void worldTimeExtremesDoNotOverflowOrFreezeAi() {
        for (long start : new long[]{Long.MIN_VALUE, Long.MAX_VALUE - 9}) {
            for (int id : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
                int retained = 0;
                for (int tick = 0; tick < 10; tick++) {
                    if (PokemonAiBudget.shouldThink(start + tick, id)) retained++;
                }
                assertEquals(3, retained);
            }
        }
    }

    @Test void scheduleDoesNotAccumulateCatchUpWorkOrDependOnOtherEntities() {
        boolean expected = PokemonAiBudget.shouldThink(123456789L, 42);
        for (int i = 0; i < 1000; i++) PokemonAiBudget.shouldThink(i, i);
        assertEquals(expected, PokemonAiBudget.shouldThink(123456789L, 42));
    }

    @Test void commonConfigRegistersOnlyPokemonBrainThrottle() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("sre-pokemon-compat-performance.mixins.json")) {
            assertNotNull(stream);
            var config = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.getAsJsonArray("mixins").asList().stream()
                    .anyMatch(value -> value.getAsString().equals("PokemonAiBudgetMixin")));
        }
        var node = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "io/wifi/srepackcompat/mixin/server/PokemonAiBudgetMixin.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        var handler = node.methods.stream().filter(m -> m.name.equals("sre$thinkThirtyPercent")).findFirst().orElseThrow();
        var annotation = handler.visibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/WrapWithCondition;")).findFirst().orElseThrow();
        assertTrue(annotation.values.toString().contains("customServerAiStep"));
        assertTrue(annotation.values.toString().contains("method_5958"));
        var calls = new ArrayList<String>();
        for (var instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call.name);
        }
        assertEquals(List.of("getGameTime", "getId", "shouldThink"), calls);
        assertTrue(node.invisibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Pseudo;")));
    }
}
