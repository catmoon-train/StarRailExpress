package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

import java.nio.file.Files;
import java.nio.file.Path;
import org.agmas.noellesroles.game.minigame.roulette.RouletteBalance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouletteDevilSafetyTest {
    private static final Path JAVA = Path.of("src/main/java/org/agmas/noellesroles");
    @Test void initialReductionHappensAfterRoundingAndNeverGoesNegative() {
        assertEquals(0, RouletteBalance.initialSupply(0));
        assertEquals(0, RouletteBalance.initialSupply(2));
        assertEquals(1, RouletteBalance.initialSupply(3));
        assertEquals(2, RouletteBalance.initialSupply(4));
        assertEquals(3, RouletteBalance.initialSupply(5));
        for (int raw = 2; raw <= 5; raw++) assertEquals(RouletteBalance.supply(raw) - 1, RouletteBalance.initialSupply(raw));
    }
    @Test void operationWindowIsExactlySeventeenSecondsAndDoesNotAccelerateOnIdle() throws Exception {
        assertEquals(17, RouletteBalance.TURN_SECONDS); assertEquals(340, RouletteBalance.TURN_TICKS);
        String table = read("content/block_entity/DevilRouletteTableEntity.java");
        assertTrue(table.contains("AFK_TICK = RouletteBalance.TURN_TICKS"));
        assertTrue(table.contains("private int budget() { return AFK_TICK; }"));
        assertTrue(table.contains("amount, firstSupply)"));
        assertTrue(table.contains("firstSupply = true;")); assertTrue(table.contains("firstSupply = false;"));
    }
    @Test void everyExternalDeathGateUsesSettlementPermissionRatherThanDuelMembership() throws Exception {
        String role = read("game/roles/neutral/roulette_devil/RouletteDevilRole.java");
        int start = role.indexOf("GameUtilityEvents.BEFORE_KILL.register(");
        String gates = role.substring(start, role.indexOf("ServerMessageEvents.ALLOW_CHAT_MESSAGE", start));
        assertTrue(gates.contains("ALLOW_DAMAGE.register")); assertTrue(gates.contains("ALLOW_DEATH.register"));
        assertFalse(gates.contains("inDuel("));
        assertEquals(3, gates.split("deathAllowed\\(p\\)", -1).length - 1);
        assertTrue(role.contains("return deathAllowed(victim);"));
        int helper = role.indexOf("public static boolean deathAllowed(");
        String permission = role.substring(helper, role.indexOf("@Override", helper));
        assertTrue(permission.contains("RouletteDevilRules.allowDeath"));
        assertTrue(permission.contains("RouletteDevilSessions.settlingDeath"));
        assertFalse(role.contains("setInvulnerable(!duel)"));
    }
    @Test void legalFinalDefeatStillUsesAScopedDeathException() throws Exception {
        String session = read("game/roles/neutral/roulette_devil/RouletteDevilSessions.java");
        int settle = session.indexOf("private static void settle(");
        String body = session.substring(settle, session.indexOf("public static void cancel(", settle));
        assertTrue(body.contains("RouletteDevilRules.spareDefeat"));
        assertTrue(body.indexOf("DEATHS.add(loser)") < body.indexOf("GameUtils.killPlayer("));
        assertTrue(body.contains("finally { DEATHS.remove(loser); }"));
        assertTrue(body.contains("if (victim != null && GameUtils.isPlayerAliveAndSurvival(victim))"));
        assertTrue(body.contains("if (!spared) {"));
    }
    @Test void sparedLoserGetsAThreeMinuteDisabilityButTheKilledOneDoesNot() throws Exception {
        String session = read("game/roles/neutral/roulette_devil/RouletteDevilSessions.java");
        int settle = session.indexOf("private static void settle(");
        String body = session.substring(settle, session.indexOf("public static void cancel(", settle));
        // 只有免死分支施加残疾，致命分支直接处死。
        int sparedBranch = body.indexOf("} else {", body.indexOf("if (!spared) {"));
        assertTrue(sparedBranch > 0);
        assertTrue(body.indexOf("applyLoserDisability(victim)") > sparedBranch);
        assertTrue(body.indexOf("GameUtils.killPlayer(") < sparedBranch);
        int helper = session.indexOf("private static void applyLoserDisability(");
        String helperBody = session.substring(helper, session.indexOf("public static void cancelAllForRefugee(", helper));
        assertTrue(helperBody.contains("DisabledPatientRoleData.applyRandomTemporaryNamed("));
        assertTrue(helperBody.contains("RouletteDevilRules.LOSER_DISABILITY_TICKS"));
        assertTrue(helperBody.contains("\"message.noellesroles.roulette_devil.loser_disability\""));
        // 复用残疾患者自己的权重表，不另抄一份效果清单。
        String patient = read("role_data/innocence/DisabledPatientRoleData.java");
        assertTrue(patient.contains("public static Disability applyRandomTemporaryNamed("));
        assertTrue(patient.contains("return picked.applyTemporary(player, durationTicks) ? picked : null;"));
        assertTrue(patient.contains("public boolean applyTemporary(ServerPlayer player, int durationTicks) {"));
    }
    @Test void realSanIsCapturedBeforeProtectionAndRestoredOnlyForProtectedPlayers() throws Exception {
        String session = read("game/roles/neutral/roulette_devil/RouletteDevilSessions.java");
        int capture = session.indexOf(".capture(p);");
        assertTrue(capture >= 0 && capture < session.indexOf("keepSanHigh(p);"));
        assertTrue(session.contains("keepSanHigh(other);"));
        assertTrue(session.contains("SRERole.MoodType.REAL"));
        assertTrue(session.contains("mood.setMood(mood.getMoodMax())"));
        assertTrue(session.contains("ModEffects.MOOD_DRAIN_IMMUNITY, -1, 0, true, false, false"));
        assertTrue(session.contains("p.setInvulnerable(RouletteDevilRole.isDevil(p))"));
        String recovery = read("game/roles/neutral/roulette_devil/RouletteDevilRecovery.java");
        assertTrue(recovery.contains("tag.putFloat(\"DuelMood\""));
        assertTrue(recovery.contains("if (tag.contains(\"DuelMood\"))"));
        assertTrue(recovery.contains("setMood(tag.getFloat(\"DuelMood\"))"));
        assertTrue(recovery.contains("restoreEffect(p, tag, \"MoodImmunity\")"));
    }
    @Test void lanternsHaveSupportsAndDoNotOccupySeatsOrTheThreeByThreeTable() throws Exception {
        String room = read("game/roles/neutral/roulette_devil/RouletteDevilRoom.java");
        assertTrue(room.contains("new int[]{-2, 2}"));
        assertTrue(room.contains("floor.offset(x, 1, 0), Blocks.POLISHED_DEEPSLATE"));
        assertTrue(room.contains("floor.offset(x, 2, 0), Blocks.LANTERN"));
        assertFalse(room.contains("REDSTONE_TORCH"));
        assertTrue(room.contains("Direction.NORTH, front ? 2 : -2"));
    }
    @Test void sameTargetCooldownIsWrittenWhenADuelEndsAndNotOnFailedEntry() throws Exception {
        String session = read("game/roles/neutral/roulette_devil/RouletteDevilSessions.java");
        int close = session.indexOf("private static void close(Session s, MinecraftServer server) {");
        String body = session.substring(close, session.indexOf("public static void syncAll(", close));
        assertTrue(body.contains("if (s.entered) {"));
        assertTrue(body.contains("s.data.startCooldown(s.opponent, now);"));
        assertTrue(body.indexOf("s.data.startCooldown(") < body.indexOf("s.data.sync();"));
        // 挑战前的冷却拒绝必须在分配房间之前，避免冷却期内白建黑室。
        int guard = session.indexOf("data.cooling(opponent.getUUID(), now)");
        assertTrue(guard > 0 && guard < session.indexOf("RouletteDevilRoom.create("));
        assertTrue(session.contains("\"message.noellesroles.roulette_devil.cooldown\""));
        String data = read("role_data/neutral/RouletteDevilRoleData.java");
        assertTrue(data.contains("public final Map<UUID, Long> retryAt = new HashMap<>();"));
        assertTrue(data.contains("tag.put(\"Cooldowns\", cooldowns);"));
        assertTrue(data.contains("tag.getList(\"Cooldowns\", Tag.TAG_COMPOUND)"));
        assertTrue(data.contains("retryAt.clear();"));
        String role = read("game/roles/neutral/roulette_devil/RouletteDevilRole.java");
        int reset = role.indexOf("OnGameStarted.EVENT.register(");
        String resetBody = role.substring(reset, role.indexOf("});", reset));
        assertTrue(resetBody.contains("d.seeds.clear()"));
    }
    @Test void meetingPausesTheDuelAndKeepsBothPlayersOut() throws Exception {
        String session = read("game/roles/neutral/roulette_devil/RouletteDevilSessions.java");
        assertTrue(session.contains("MeetingApi.isMeetingActive()"));
        assertTrue(session.contains("if (meeting) { pause(s); continue; }"));
        assertTrue(session.contains("if (s.paused) resume(s);"));
        assertTrue(session.contains("MeetingParticipantEvent.ALLOW_PARTICIPANT.register("));
        assertTrue(session.contains("player -> !inDuel(player.getUUID())"));
        int pause = session.indexOf("private static void pause(Session s) {");
        String pauseBody = session.substring(pause, session.indexOf("private static void resume", pause));
        assertTrue(pauseBody.contains("s.table.pauseForMeeting("));
        String table = read("content/block_entity/DevilRouletteTableEntity.java");
        assertTrue(table.contains("private long meetingPausedAt;"));
        assertTrue(table.contains("public void pauseForMeeting(long now)"));
        assertTrue(table.contains("startedAt += frozen;"));
        assertTrue(table.contains("round().ended() || pausedForMeeting()"));
    }
    @Test void manifestationDrainsFixedSanInsteadOfMultiplyingNaturalDrain() throws Exception {
        String role = read("game/roles/neutral/roulette_devil/RouletteDevilRole.java");
        assertTrue(role.contains("now % RouletteDevilRules.GAZE_INTERVAL_TICKS == 0"));
        assertTrue(role.contains("RouletteDevilRules.gazeSanLoss(mood.getMoodMax())"));
        assertTrue(role.contains("mood.setMood(Math.max(0f, mood.getMood() - loss))"));
        assertFalse(role.contains("ROULETTE_DEVIL_DREAD"));
        String effects = read("init/ModEffects.java");
        assertFalse(effects.contains("manifestationDrain"));
    }
    @Test void instinctOutlineUsesOneColourForEveryPlayer() throws Exception {
        String client = read("client/RouletteDevilClient.java");
        int register = client.indexOf("RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(");
        String body = client.substring(register, client.indexOf("private static void accept(", register));
        assertFalse(body.contains("isLowSan("));
        assertFalse(body.contains("isMature("));
        assertTrue(body.contains("return TrueFalseAndCustomResult.custom(OBSERVER_COLOR);"));
        assertTrue(client.contains("OBSERVER_COLOR = 0xFF3388FF"));
    }
    @Test void meetingManagerFiltersParticipantsAndVoteOptions() throws Exception {
        String meeting = Files.readString(Path.of("src/main/java/net/exmo/sre/meeting/MeetingManager.java"))
                .replace("\r\n", "\n");
        assertTrue(meeting.contains("MeetingParticipantEvent.ALLOW_PARTICIPANT.invoker()::allowParticipant"));
        assertTrue(meeting.contains("private static long participantPopulation(ServerLevel serverLevel) {"));
        assertTrue(meeting.contains(".filter(p -> participants.containsKey(p.getUUID()))"));
    }
    private static String read(String path) throws Exception {
        return Files.readString(JAVA.resolve(path)).replace("\r\n", "\n");
    }
}
