package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FakeSteveDeathReviveTest {
    @Test
    void waitsThirtyFiveSecondsAndHasExactlyFifteenPercentChance() {
        assertEquals(700L, FakeSteveRules.DEATH_REVIVE_DELAY_TICKS);
        assertEquals(15, FakeSteveRules.DEATH_REVIVE_CHANCE_PERCENT);
        int successes = 0;
        for (int roll = 0; roll < 100; roll++) {
            if (FakeSteveRules.shouldReviveAfterDeath(roll)) successes++;
        }
        assertEquals(15, successes);
        assertFalse(FakeSteveRules.shouldReviveAfterDeath(-1));
        assertTrue(FakeSteveRules.shouldReviveAfterDeath(14));
        assertFalse(FakeSteveRules.shouldReviveAfterDeath(15));
        assertFalse(FakeSteveRules.shouldReviveAfterDeath(100));
    }

    @Test
    void onlyFinalDeathsAreQueuedAndRevivalRequiresValidOwnRoom() throws Exception {
        String source = director();
        assertTrue(source.contains("OnPlayerDeathWithKiller.FINAL_EVENT.register"));
        assertFalse(source.contains("OnPlayerDeath.EVENT.register"));
        assertTrue(source.contains("player.isSpectator() && !GameUtils.isPlayerAliveAndSurvival(player)"));
        assertTrue(source.contains("session.deathReviveAttempted.add(player.getUUID())"));
        String revive = source.substring(source.indexOf("private static void tryReviveDeadPlayer("),
                source.indexOf("private static void clearPlayerBodies("));
        assertTrue(revive.contains("!isReallyDead(player)"));
        assertTrue(revive.contains("session(level) != session"));
        assertTrue(revive.contains("!canGenerate(level) || !isRoundAcceptingCommands(level)"));
        assertTrue(revive.indexOf("room == null") < revive.indexOf("GameUtils.revivePlayerToItsRoom(player)"));
        assertTrue(revive.contains("GameUtils.getSpawnPos(AreasWorldComponent.KEY.get(level), room) == null"));
        assertTrue(revive.contains("clearPlayerBodies(level, player)"));
        assertTrue(revive.contains("replace(player, ReplacementCause.DEATH_REVIVE)"));
        assertTrue(revive.indexOf("GameUtils.revivePlayerToItsRoom(player)")
                < revive.indexOf("replace(player, ReplacementCause.DEATH_REVIVE)"));
        assertTrue(revive.indexOf("clearPlayerBodies(level, player)")
                < revive.indexOf("replace(player, ReplacementCause.DEATH_REVIVE)"));
        assertTrue(revive.indexOf("replace(player, ReplacementCause.DEATH_REVIVE)")
                < revive.indexOf("SRENetworkMessageUtils.sendActionbar"));
        assertTrue(source.contains("player.getUUID().equals(body.getPlayerUuid())"));
        assertTrue(source.contains("bodies.forEach(PlayerBodyEntity::discard)"));
    }

    @Test
    void deathTimersAreRoundLocalAndCannotRunAfterResetOrGameEnd() throws Exception {
        String source = director();
        String tick = source.substring(source.indexOf("private static void tickDeathRevives("),
                source.indexOf("private static void tryReviveDeadPlayer("));
        assertTrue(tick.contains("session(level) != session"));
        assertTrue(tick.contains("session.pendingDeathRevives.clear()"));
        assertTrue(tick.contains("player == null || player.serverLevel() != level || !isReallyDead(player)"));
        assertTrue(tick.contains("level.getGameTime() < session.pendingDeathRevives.get(id)"));
        assertTrue(tick.lastIndexOf("session.pendingDeathRevives.remove(id)")
                < tick.indexOf("tryReviveDeadPlayer(level, session, player)"));
        assertTrue(source.contains("OnGameEnd.EVENT.register((level, game) -> clear(level))"));
        assertTrue(source.contains("previous.pendingDeathRevives.clear()"));
        assertTrue(source.contains("removed.pendingDeathRevives.clear()"));
        String end = source.substring(source.indexOf("public static void onEventRoundEnd("),
                source.indexOf("private static Session resetSession("));
        assertTrue(end.contains("resetSession(level)"));
    }

    private static String director() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/fake_steve/FakeSteveDirector.java"));
    }
}
