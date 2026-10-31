package org.agmas.noellesroles.game.command_block;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CommandBlockCrackSpawnTest {
    @Test void sixtyBlocksIsInclusiveAndInvalidDistancesFail() {
        assertTrue(CommandBlockCrackSpawnRules.withinPlayerRange(0));
        assertTrue(CommandBlockCrackSpawnRules.withinPlayerRange(3600));
        assertFalse(CommandBlockCrackSpawnRules.withinPlayerRange(3600.001));
        assertFalse(CommandBlockCrackSpawnRules.withinPlayerRange(-1));
        assertFalse(CommandBlockCrackSpawnRules.withinPlayerRange(Double.NaN));
        assertFalse(CommandBlockCrackSpawnRules.withinPlayerRange(Double.POSITIVE_INFINITY));
    }

    @Test void fifthCandidateCanSucceedButSixthIsNeverChecked() {
        var calls = new AtomicInteger();
        assertEquals(Integer.valueOf(5), CommandBlockCrackSpawnRules.choose(List.of(1, 2, 3, 4, 5, 6), n -> {
            calls.incrementAndGet();
            return n == 5 ? n : null;
        }));
        assertEquals(5, calls.get());
        calls.set(0);
        assertNull(CommandBlockCrackSpawnRules.choose(List.of(1, 2, 3, 4, 5, 6), n -> {
            calls.incrementAndGet();
            return n == 6 ? n : null;
        }));
        assertEquals(5, calls.get());
    }

    @Test void stopsOnFirstSuccessAndHandlesFewOrNoCandidates() {
        var calls = new AtomicInteger();
        assertEquals(Integer.valueOf(1), CommandBlockCrackSpawnRules.choose(List.of(1, 2), n -> {
            calls.incrementAndGet(); return n;
        }));
        assertEquals(1, calls.get());
        assertNull(CommandBlockCrackSpawnRules.choose(List.of(), n -> fail("empty list")));
        calls.set(0);
        assertNull(CommandBlockCrackSpawnRules.choose(List.of(1, 2), n -> {
            calls.incrementAndGet(); return null;
        }));
        assertEquals(2, calls.get());
    }

    @Test void productionExcludesExerciseAndChecksLivingPlayersAtFinalSpot() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/command_block/CommandBlockCracks.java"));
        assertTrue(source.contains("SREPlayerTaskComponent.Task.EXERCISE.ordinal()"));
        assertTrue(source.contains("type == chairType || type == exerciseType"));
        assertTrue(source.contains("filter(GameUtils::isPlayerAliveAndSurvival)"));
        assertTrue(source.contains("CommandBlockCrackSpawnRules.choose(points"));
        assertTrue(source.contains("withinPlayerRange(player.distanceToSqr(spot))"));
        assertTrue(source.indexOf("discard(level);", source.indexOf("boolean spawnOne"))
                < source.indexOf("Vec3 spot = chooseSpot(level)"));
    }
}
