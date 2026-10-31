package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenSawFeedbackTest {
    @Test void innocentBagsExcludeSawBeforeWeightedDrawIncludingOldConfigurations() {
        var saw = new HalloweenConfig.Loot("noellesroles:halloween_blood_saw", 1, 1000);
        var candy = new HalloweenConfig.Loot("noellesroles:halloween_candy", 2, 1);
        var pumpkin = new HalloweenConfig.Loot("noellesroles:halloween_pumpkin", 2, 3);
        for (int i = 0; i < 100; i++) {
            assertNotSame(saw, HalloweenConfig.rollCandyBagLoot(List.of(saw, candy, pumpkin), i / 100.0, true));
        }
        assertSame(saw, HalloweenConfig.rollCandyBagLoot(List.of(saw, candy), 0, false));
        assertNull(HalloweenConfig.rollCandyBagLoot(List.of(saw), 0, true));
        assertSame(candy, HalloweenConfig.rollCandyBagLoot(List.of(saw, candy), 0.99, true));
        assertEquals(1000, saw.weight);
    }

    @Test void sawVictimIsNotifiedAtStartAndParticlesRequireFinalConfirmedKill() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/halloween/HalloweenCombat.java"));
        String start = source.substring(source.indexOf("public static boolean startSaw("), source.indexOf("public static boolean fireGun("));
        assertTrue(start.contains("target.displayClientMessage"));
        assertTrue(start.contains("halloween.saw_attacked"));
        String death = source.substring(source.indexOf("private static void onDeath("), source.indexOf("private static boolean legalBody("));
        assertTrue(death.contains("alive(victim)) return"));
        assertTrue(death.indexOf("SAW_DEATH.equals(reason) && reason.equals(r.attempts.get(uuid))")
                < death.indexOf("ParticleFx.burst(level"));
        assertEquals(1, death.split("ParticleFx.burst", -1).length - 1);
    }

    @Test void pumpkinSpiritUsesEnabledMurderDerivedMapNotExactBaseMode() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/halloween/pumpkin/PumpkinSpiritRole.java"));
        assertTrue(source.contains("HalloweenRuntime.isMapEnabled(world)"));
        assertFalse(source.contains("SREGameModes.MURDER"));
        assertTrue(source.contains("super.canBeRandomed(world)"));
        assertTrue(source.contains("Math.min(1, configured)"));
    }
}
