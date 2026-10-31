package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenSawCooldownTest {
    @Test void sawStartsFortySecondCooldownOnlyAfterTargetValidation() throws Exception {
        assertEquals(800, HalloweenRewardRules.SAW_COOLDOWN_TICKS);
        String source = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenCombat.java"));
        String start = source.substring(source.indexOf("public static boolean startSaw("),
                source.indexOf("public static boolean fireGun("));
        assertTrue(start.contains("r.sawReady.getOrDefault(p.getUUID(), 0L) > r.state.now()"));
        int cooldown = start.indexOf("r.sawReady.put(p.getUUID(), r.state.now() + HalloweenRewardRules.SAW_COOLDOWN_TICKS)");
        assertTrue(cooldown > start.indexOf("!visible(p, chest)) return false"));
        assertTrue(start.contains("p.getCooldowns().addCooldown(HalloweenItems.BLOOD_SAW, HalloweenRewardRules.SAW_COOLDOWN_TICKS)"));
        String end = source.substring(source.indexOf("private static void endSaw("), source.indexOf("private static void give("));
        assertFalse(end.contains("sawReady.remove"), "中断不得返还冷却");
        assertTrue(source.contains("if (saw > 0) p.getCooldowns().addCooldown(HalloweenItems.BLOOD_SAW, (int) saw)"));
        assertTrue(source.contains("r.sawReady.remove(uuid)"));
        assertEquals(2, source.split("removeCooldown\\(HalloweenItems.BLOOD_SAW\\)", -1).length - 1);
    }
}
