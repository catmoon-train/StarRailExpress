package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 死亡链依赖运行中的 Minecraft；这里锁定强杀入口、作用范围和前置拦截的源码契约。 */
class FakeSteveHuntDamageTest {
    @Test
    void onlyLivingFakeAgainstHumanInAnActiveHuntCanForceDamage() throws Exception {
        String source = read("org/agmas/noellesroles/game/fake_steve/FakeSteveDirector.java");
        String guard = between(source, "public static boolean isForcedHuntAttack(", "public static boolean activate(");
        assertTrue(guard.contains("attacker instanceof ServerPlayer fake"));
        assertTrue(guard.contains("victim instanceof ServerPlayer human"));
        assertTrue(guard.contains("fake == human || fake.serverLevel() != human.serverLevel()"));
        assertTrue(guard.contains("!GameUtils.isPlayerAliveAndSurvival(fake)"));
        assertTrue(guard.contains("!GameUtils.isPlayerAliveAndSurvival(human)"));
        assertTrue(guard.contains("!isReplaced(fake) || isReplaced(human)"));
        assertTrue(guard.contains("session.active && session.huntPhase && !session.victoryDeclared"));
        assertTrue(guard.contains("canGenerate(level) && isRoundAcceptingCommands(level) && !MeetingManager.isActive()"));
        // 手控与 AI 同权，不应根据当前是否手控缩小强杀作用范围。
        assertFalse(guard.contains("isManualControl"));
    }

    @Test
    void forceFlagIsSetOnServerBeforeContextAndDeathVetoes() throws Exception {
        String source = read("io/wifi/starrailexpress/game/GameDeath.java");
        String resolve = source.substring(source.indexOf("private static void killPlayerOnServerThread("));
        int upgrade = resolve.indexOf("forceDeath = forceDeath || FakeSteveDirector.isForcedHuntAttack(_killer, victim);");
        assertTrue(upgrade >= 0);
        assertTrue(upgrade < resolve.indexOf("forcedDeathInProgress = forceDeath;"));
        assertTrue(upgrade < resolve.indexOf("gameMode.killPlayer(victim, spawnBody, _killer, deathReason, forceDeath);"));
        assertTrue(resolve.contains("forcedDeathInProgress = previousForced;"));
        String mode = read("io/wifi/starrailexpress/api/GameMode.java");
        assertTrue(mode.contains("if (c != null && c.isFalse() && (!forceDeath))"));
        assertTrue(mode.contains("canDeath = canDeath || forceDeath;"));
        assertTrue(mode.contains("OnPlayerDeathWithKiller.FINAL_EVENT"));
    }

    @Test
    void backworldMeleeVetoAllowsOnlyTheSameForcedHuntAttack() throws Exception {
        String melee = read("org/agmas/noellesroles/mixin/roles/nostalgist/NostalgistKnifeImmuneMixin.java");
        assertTrue(melee.contains("comp != null && comp.isActiveBackWorld()"));
        assertTrue(melee.contains("&& !FakeSteveDirector.isForcedHuntAttack(attacker, target)"));
        assertTrue(melee.contains("ci.cancel();"));
        String commandCombat = read("org/agmas/noellesroles/game/command_block/CommandBlockCombat.java");
        String death = between(commandCombat, "public static boolean allowScriptDeath(", "public static boolean dealHimSword(");
        assertTrue(death.indexOf("if (forced || isTrain(reason))")
                < death.indexOf("game.isRole(victim, ModRoles.HIM) && isHimInvisible(victim)"));
        String knife = read("io/wifi/starrailexpress/network/original/KnifeStabPayload.java");
        assertTrue(knife.contains("if (target.distanceTo(player) > 3.0)"));
        assertTrue(knife.contains("GameUtils.killPlayer(target, true, player, deathReason);"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/java", path));
    }

    private static String between(String source, String start, String end) {
        int begin = source.indexOf(start);
        return source.substring(begin, source.indexOf(end, begin));
    }
}
