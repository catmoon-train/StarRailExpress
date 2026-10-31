package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenCostumeDeathTest {
    @Test void accidentalKillPenaltyBypassesCostumeBeforeAnyConsumption() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenConsumables.java"));
        String guard = source.substring(source.indexOf("public static boolean tryBlockDamage(Player player, ResourceLocation reason)"),
                source.indexOf("public static boolean tryBlockDamage(Player player)"));
        assertTrue(guard.contains("GameConstants.DeathReasons.SHOT_INNOCENT.equals(reason)) return false;"));
        assertTrue(guard.indexOf("SHOT_INNOCENT.equals(reason)") < guard.indexOf("return tryBlockDamage(player)"));
        assertFalse(guard.contains("setItemSlot"));
        String runtime = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenRuntime.java"));
        assertEquals(2, runtime.split("tryBlockDamage\\(victim, reason\\)", -1).length - 1);
        assertFalse(runtime.contains("tryBlockDamage(victim)"));
    }
}
