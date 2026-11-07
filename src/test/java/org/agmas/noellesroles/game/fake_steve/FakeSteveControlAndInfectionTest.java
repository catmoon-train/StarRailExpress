package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FakeSteveControlAndInfectionTest {
    @Test
    void tenSecondsOfControlThenEightySecondsOfCooldown() {
        FakeSteveManualControl control = new FakeSteveManualControl();
        assertEquals(200L, FakeSteveRules.MANUAL_CONTROL_TICKS);
        assertEquals(1600L, FakeSteveRules.MANUAL_CONTROL_COOLDOWN_TICKS);
        assertTrue(control.start(100L));
        assertTrue(control.isActive(100L));
        assertTrue(control.isActive(299L));
        assertFalse(control.start(150L));
        assertEquals(150L, control.remainingTicks(150L));
        assertFalse(control.isActive(300L));
        assertTrue(control.finish(300L));
        assertEquals(1600L, control.cooldownTicks(300L));
        assertFalse(control.finish(301L));
        assertFalse(control.start(1899L));
        assertTrue(control.start(1900L));
    }

    @Test
    void aLateTickDoesNotExtendTheCooldown() {
        FakeSteveManualControl control = new FakeSteveManualControl();
        assertTrue(control.start(100L));
        assertTrue(control.finish(350L));
        assertEquals(1550L, control.cooldownTicks(350L));
        assertTrue(control.start(1900L));
    }

    @Test
    void earlyCancellationStartsCooldownImmediatelyAndCannotBeRefreshed() {
        FakeSteveManualControl control = new FakeSteveManualControl();
        assertTrue(control.start(100L));
        assertTrue(control.finish(135L));
        assertFalse(control.hasControl());
        assertFalse(control.isActive(136L));
        assertEquals(1600L, control.cooldownTicks(135L));
        assertFalse(control.finish(200L));
        assertFalse(control.start(1734L));
        assertTrue(control.start(1735L));
        assertTrue(new FakeSteveManualControl().start(0L));
    }

    @Test
    void infectionIsQueuedOnlyAfterFinalDeathAndConvertedOutsideDeathCallback() throws Exception {
        String source = read("game/fake_steve/FakeSteveDirector.java");
        String callback = between(source, "OnPlayerDeathWithKiller.FINAL_EVENT.register", "AllowGameEnd.EVENT_END.register");
        assertTrue(callback.contains("!isReallyDead(player)"));
        assertTrue(callback.contains("isReplaced(killer)"));
        assertTrue(callback.contains("new PendingInfection(player, player.position(), level.getGameTime() + 1L)"));
        assertFalse(callback.contains("GameUtils.revivePlayer"));
        assertFalse(callback.contains("isManualControl"));
        String infection = between(source, "private static void tickInfections(", "private static void tickDeathRevives(");
        assertTrue(infection.contains("session(level) != session"));
        assertTrue(infection.contains("!canGenerate(level) || !isRoundAcceptingCommands(level) || MeetingManager.isActive()"));
        assertTrue(infection.contains("player != pending.player() || player.serverLevel() != level || !isReallyDead(player)"));
        assertTrue(infection.contains("level.getGameTime() < pending.dueTick()"));
        assertTrue(infection.indexOf("session.pendingInfections.remove(id);", infection.indexOf("pending.dueTick()"))
                < infection.indexOf("GameUtils.revivePlayer(player, pos.x, pos.y, pos.z)"));
        assertTrue(infection.indexOf("GameUtils.revivePlayer(player, pos.x, pos.y, pos.z)")
                < infection.indexOf("clearPlayerBodies(level, player)"));
        assertTrue(infection.indexOf("clearPlayerBodies(level, player)")
                < infection.indexOf("replace(player, ReplacementCause.INFECTION)"));
        assertTrue(source.contains("previous.pendingInfections.clear()"));
        assertTrue(source.contains("removed.pendingInfections.clear()"));
        assertTrue(source.contains("session.level == level ? session : null"));
        assertTrue(source.contains("!session.pendingInfections.isEmpty() && proposed != GameUtils.WinStatus.TIME"));
    }

    @Test
    void abilityKeyBypassesItsOwnBanWithoutTriggeringOriginalSkills() throws Exception {
        String client = read("client/NoellesrolesClient.java");
        assertTrue(client.indexOf("FakeSteveClient.handleAbilityKey(client, abilityPressed)")
                < client.indexOf("EchoRollClient.handleAbility(client)"));
        String key = read("mixin/client/general/MobEffectKeyMixin.java");
        assertTrue(key.indexOf("FakeSteveDirector.isReplaced(player)") < key.indexOf("player.hasEffect(ModEffects.SKILL_BANED)"));
        String receiver = between(read("init/ModPacketsReciever.java"),
                "registerGlobalReceiver(ModPackets.ABILITY_PACKET", "registerGlobalReceiver(ModPackets.INSANE_KILLER_ABILITY_PACKET");
        assertTrue(receiver.indexOf("FakeSteveDirector.handleAbilityInput(context.player())")
                < receiver.indexOf("context.player().hasEffect(ModEffects.SAFE_TIME)"));
        assertTrue(receiver.contains("payload.phase() == RoleSkill.Phase.PRESS"));
        assertTrue(receiver.contains("!org.agmas.noellesroles.game.fake_steve.FakeSteveDirector.isReplaced(context.player())"));
        String director = read("game/fake_steve/FakeSteveDirector.java");
        String manual = between(director, "private static void applyManualControl(", "public static int pendingEvents(");
        for (String effect : new String[] {"MOVE_BANED", "TURN_BANED", "USED_BANED", "INVENTORY_BANED"}) {
            assertTrue(manual.contains("player.removeEffect(ModEffects." + effect + ")"));
        }
        assertTrue(manual.contains("addControl(player, ModEffects.SKILL_BANED)"));
        assertFalse(manual.contains("removeEffect(ModEffects.SKILL_BANED)"));
        assertTrue(read("mixin/fake_steve/FakeSteveServerInputMixin.java").contains("!FakeSteveDirector.isManualControl(self.player)"));
    }

    @Test
    void handoffCancelsForMeetingsAndDeathAndResetsAiWithoutTeleportRescue() throws Exception {
        String source = read("game/fake_steve/FakeSteveDirector.java");
        String finish = between(source, "private static void finishManualControl(", "private static void applyManualControl(");
        assertTrue(finish.contains("FakeSteveMotionController.clear(player, state)"));
        assertTrue(finish.contains("state.path.clear()"));
        assertTrue(finish.contains("state.pathGoal = null"));
        assertTrue(finish.contains("state.nextPathTick = now"));
        assertTrue(finish.contains("FakeSteveAi.resetStuckTracking(player, state, now)"));
        String tick = between(source, "private static void tick(ServerLevel", "private static void applyControl(");
        assertTrue(tick.contains("!control.isActive(level.getGameTime()) || MeetingManager.isActive()"));
        assertTrue(tick.contains("!GameUtils.isPlayerAliveAndSurvival(player)"));
        assertTrue(tick.contains("clearSessionControls(level, session)"));
        assertTrue(tick.indexOf("if (isManualControl(player))") < tick.indexOf("FakeSteveAi.tick(level, player"));
        assertFalse(source.contains("FakeSteveAi.rescueIfStuck("));
        assertTrue(source.contains("session.manualControls.clear()"));
        assertTrue(source.contains("removed.manualControls.clear()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles/" + path));
    }

    private static String between(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
    }
}
