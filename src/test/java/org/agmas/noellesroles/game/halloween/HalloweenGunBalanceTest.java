package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenGunBalanceTest {
    @Test void threeUsesAndThirtySecondMinimumSurviveOldConfigurations() {
        assertEquals(3, HalloweenRewardRules.GUN_USES);
        assertEquals(600, HalloweenRewardRules.gunCooldown(200));
        assertEquals(600, HalloweenRewardRules.gunCooldown(600));
        assertEquals(900, HalloweenRewardRules.gunCooldown(900));
    }
    @Test void exhaustedOldGunIsRejectedBeforeEffectsAndCooldownUsesEffectiveValue() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/halloween/HalloweenCombat.java"));
        String fire = source.substring(source.indexOf("public static boolean fireGun("), source.indexOf("private static InteractionResult attack("));
        assertTrue(fire.indexOf("getInt(\"HalloweenCombatUses\") >= GUN_USES") < fire.indexOf("gunFx(p, target)"));
        assertTrue(fire.contains("HalloweenRewardRules.gunCooldown(HalloweenConfig.get().gunCooldownTicks)"));
        assertTrue(fire.contains("r.state.now() + cooldown"));
        assertTrue(fire.contains("addCooldown(HalloweenItems.FRIGHT_GUN, cooldown)"));
        assertTrue(fire.contains("spendUse(p, stack, GUN_USES)"));
    }
}
