package io.wifi.srepackcompat.cobblemon;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveSpawnBudgetTest {
    private static void ticks(AdaptiveSpawnBudget budget, int count, long millis) {
        for (int i = 0; i < count; i++) budget.observeAverageTickNanos(millis * 1_000_000L);
    }

    @Test void initialAndHealthyBudgetAreFortyPercent() {
        var budget = new AdaptiveSpawnBudget();
        assertEquals(0.4F, budget.fraction());
        ticks(budget, 2000, 20);
        assertEquals(0.4F, budget.fraction());
        assertEquals(0.4F, AdaptiveSpawnBudget.targetFraction(40_000_000L));
    }

    @Test void targetsInterpolateContinuouslyBetweenFortyAndOneHundredMillis() {
        assertEquals(0.35F, AdaptiveSpawnBudget.targetFraction(50_000_000L), 0.000001F);
        assertEquals(0.25F, AdaptiveSpawnBudget.targetFraction(70_000_000L), 0.000001F);
        assertEquals(0.15F, AdaptiveSpawnBudget.targetFraction(90_000_000L), 0.000001F);
        assertEquals(0.1F, AdaptiveSpawnBudget.targetFraction(100_000_000L));
    }

    @Test void worseningLoadDropsBudgetAtNextTwentyTickEvaluation() {
        var budget = new AdaptiveSpawnBudget();
        ticks(budget, 19, 150);
        assertEquals(0.4F, budget.fraction());
        ticks(budget, 1, 150);
        assertEquals(0.1F, budget.fraction());
    }

    @Test void prolongedExtremeLoadNeverPausesSpawning() {
        var budget = new AdaptiveSpawnBudget();
        for (int i = 0; i < 10000; i++) {
            budget.observeAverageTickNanos(Long.MAX_VALUE);
            assertTrue(budget.fraction() >= 0.1F);
            assertTrue(budget.fraction() <= 0.4F);
        }
        assertEquals(0.1F, budget.fraction());
    }

    @Test void recoveringLoadWaitsOneHundredTicksThenRisesOnlyTwoPoints() {
        var budget = new AdaptiveSpawnBudget();
        ticks(budget, 20, 150);
        ticks(budget, 99, 20);
        assertEquals(0.1F, budget.fraction());
        ticks(budget, 1, 20);
        assertEquals(0.12F, budget.fraction(), 0.000001F);
        ticks(budget, 100, 20);
        assertEquals(0.14F, budget.fraction(), 0.000001F);
    }

    @Test void repeatedDeteriorationResetsRecoveryStreak() {
        var budget = new AdaptiveSpawnBudget();
        ticks(budget, 20, 150);
        ticks(budget, 80, 20);
        ticks(budget, 20, 150);
        ticks(budget, 80, 20);
        assertEquals(0.1F, budget.fraction());
        ticks(budget, 20, 20);
        assertEquals(0.12F, budget.fraction(), 0.000001F);
    }

    @Test void longRecoveryCannotExceedNormalBudget() {
        var budget = new AdaptiveSpawnBudget();
        ticks(budget, 20, 150);
        ticks(budget, 4000, 0);
        assertEquals(0.4F, budget.fraction());
    }

    @Test void recoveryCannotOvershootCurrentLoadTarget() {
        var budget = new AdaptiveSpawnBudget();
        ticks(budget, 20, 150);
        ticks(budget, 4000, 70);
        assertEquals(0.25F, budget.fraction(), 0.000001F);
    }

    @Test void resetClearsOldLoadRecoveryStreakAndSamplingPhase() {
        var budget = new AdaptiveSpawnBudget();
        ticks(budget, 20, 150);
        ticks(budget, 89, 20);
        budget.reset();
        assertEquals(0.4F, budget.fraction());
        ticks(budget, 19, 150);
        assertEquals(0.4F, budget.fraction());
        ticks(budget, 1, 150);
        ticks(budget, 99, 20);
        assertEquals(0.1F, budget.fraction());
    }

    @Test void invalidSamplesDoNotAdvanceSamplingOrChangeBudget() {
        var budget = new AdaptiveSpawnBudget();
        ticks(budget, 19, 150);
        budget.observeAverageTickNanos(-1);
        assertEquals(0.4F, budget.fraction());
        ticks(budget, 1, 150);
        assertEquals(0.1F, budget.fraction());
    }

    @Test void differentControllersDoNotShareLoadState() {
        var first = new AdaptiveSpawnBudget();
        var second = new AdaptiveSpawnBudget();
        ticks(first, 20, 150);
        assertEquals(0.1F, first.fraction());
        assertEquals(0.4F, second.fraction());
    }

    @Test void serverEntrypointRegistersLifecycleAndTickHooksWithoutClientClasses() throws Exception {
        var node = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream("io/wifi/srepokemoncompat/PokemonCompatServer.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        var hooks = new HashSet<String>();
        var calls = new HashSet<String>();
        int registrations = 0;
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.contains("/lifecycle/")) hooks.add(field.name);
            if (instruction instanceof MethodInsnNode call) {
                assertFalse(call.owner.startsWith("net/minecraft/client/"));
                calls.add(call.name);
                if (call.name.equals("register")) registrations++;
            }
        }
        assertEquals(Set.of("SERVER_STARTING", "START_SERVER_TICK", "SERVER_STOPPED"), hooks);
        assertEquals(3, registrations);
        assertTrue(calls.containsAll(Set.of("isModLoaded", "getAverageTickTimeNanos", "observeAverageTickNanos", "resetLoadBudget")));
    }
}
