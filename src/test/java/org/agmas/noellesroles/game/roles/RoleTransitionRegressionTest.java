package org.agmas.noellesroles.game.roles;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 无需启动游戏的职业生命周期接入回归检查。 */
class RoleTransitionRegressionTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path)).replace("\r\n", "\n");
    }

    @Test
    void watcherRemovalReclaimsOnlyItsUnconsumedShieldWithoutReinitializing() throws Exception {
        String source = read("org/agmas/noellesroles/role_data/killer/WatcherRoleData.java");
        int start = source.indexOf("public void clear()");
        String clear = source.substring(start, source.indexOf("\n    }", start));
        assertFalse(clear.contains("init()"));
        assertFalse(clear.contains("giveArmor("));
        assertTrue(clear.contains("isCalm && !shieldConsumed && armorComponent.getArmor() > 0"));
        assertTrue(clear.contains("armorComponent.removeArmor();"));
        assertFalse(clear.contains("armorComponent.clear()"));
        assertTrue(clear.contains("speed.getAmplifier() == 0 && speed.isAmbient()"));
        assertTrue(clear.contains("player.removeEffect(MobEffects.MOVEMENT_SPEED)"));
    }

    @Test
    void bothNecromancersCheckActualKillerSlotsBeforeSpendingResources() throws Exception {
        String source = read("pro/fazeclan/river/stupid_express/role/necromancer/RevivalSelectionHandler.java");
        int start = source.indexOf("private static boolean hasKillerSlot");
        String check = source.substring(start, source.indexOf("\n    }", start));
        assertTrue(check.contains("RoleCountManager.getKillerCount(game.getStartingPlayerCount())"));
        assertTrue(check.contains(".filter(GameUtils::isPlayerAliveAndSurvival)"));
        assertTrue(check.contains(".filter(game::canUseKillerFeatures)"));
        assertTrue(check.contains("return aliveKillers < limit;"));
        String guard = "nc.getAvailableRevives() < 1 || !hasKillerSlot(serverLevel, gameWorldComponent)";
        assertEquals(2, source.split(java.util.regex.Pattern.quote(guard), -1).length - 1);
        int cursor = 0;
        for (int i = 0; i < 2; i++) {
            int guardAt = source.indexOf(guard, cursor);
            assertTrue(guardAt >= cursor);
            int spendAt = source.indexOf("nc.decreaseAvailableRevives()", cursor);
            assertTrue(guardAt < spendAt);
            assertTrue(guardAt < source.indexOf("cooldown.setCooldown(", cursor));
            cursor = spendAt + 1;
        }
        String world = read("io/wifi/starrailexpress/cca/SREGameWorldComponent.java");
        int allegianceAt = world.indexOf("public boolean canUseKillerFeatures(");
        String allegiance = world.substring(allegianceAt, world.indexOf("\n    }", allegianceAt));
        assertTrue(allegiance.contains("getAllegianceRole(player)"));
        assertFalse(allegiance.contains("getRole(player)"));
    }
}
