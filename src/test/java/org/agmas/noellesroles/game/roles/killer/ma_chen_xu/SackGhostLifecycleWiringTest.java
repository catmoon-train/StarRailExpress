package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 不启动Minecraft注册表的生命周期接线回归；真实联机行为另行验收。 */
class SackGhostLifecycleWiringTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles", path)).replace("\r\n", "\n");
    }

    @Test void smoothRewindGuardPrecedesSpectatorDeathCleanupAndKeepsProgress() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        String tick = role.substring(role.indexOf("@Override public void serverTick()"), role.indexOf("@Override public void clientTick()"));
        assertTrue(tick.indexOf("TimeRewind.isSmoothRewinding(owner)") < tick.indexOf("!GameUtils.isPlayerAliveAndSurvival(owner)"));
        assertTrue(tick.contains("cancelForRewind(); return;"));
        String cancel = role.substring(role.indexOf("public void cancelForRewind()"), role.indexOf("private Player rayTarget"));
        assertFalse(cancel.contains("stage ="));
        assertFalse(cancel.contains("clearOwnerArts"));
        assertTrue(cancel.contains("endOtherworld(false)"));
    }

    @Test void snapshotsFilterRealmInvulnerabilityAndPreserveCurrentRelicsOnBothRestoreExits() throws Exception {
        String rewind = source("api/time/TimeRewind.java");
        assertTrue(rewind.contains("TimeRewindEvents.CAPTURE.invoker().apply(player, vanillaState)"));
        assertTrue(rewind.indexOf("TimeRewindEvents.BEFORE_RESTORE.invoker()") < rewind.indexOf("moveToSnapshotDimension(player, snapshot, vanillaState)"));
        assertEquals(2, rewind.split(java.util.regex.Pattern.quote("TimeRewindEvents.AFTER_RESTORE.invoker()"), -1).length - 1);
        String bridge = source("game/roles/killer/ma_chen_xu/SackGhostRewind.java");
        assertTrue(bridge.contains("TimeRewindEvents.CAPTURE.register"));
        assertTrue(bridge.contains("TimeRewindEvents.BEFORE_RESTORE.register"));
        assertTrue(bridge.contains("TimeRewindEvents.AFTER_RESTORE.register"));
        assertTrue(bridge.contains("PRESERVED.remove(player.getUUID())"));
        assertTrue(bridge.contains("noellesroles:ghost_ward_relic"));
        String role = source("role_data/killer/MaChenXuRoleData.java");
        assertTrue(role.contains("state.putBoolean(\"Invulnerable\", previousInvulnerable)"));
        assertTrue(role.contains("previous = previousAt(target, effect, shared.get(effect))"));
    }

    @Test void protectedItemsAreExcludedFromEveryAreaRewindPhaseAndFromBundles() throws Exception {
        String area = source("api/time/TimeRewindAreaEngine.java");
        assertEquals(4, area.split(java.util.regex.Pattern.quote("TimeRewindEvents.ALLOW_AREA_ITEM.invoker().allow"), -1).length - 1);
        for (String item : new String[]{"GhostArtItem", "GhostWardRelicItem"}) {
            assertTrue(source("content/item/" + item + ".java").contains("canFitInsideContainerItems() { return false; }"));
        }
        String items = source("game/roles/killer/ma_chen_xu/SackGhostItems.java");
        assertTrue(items.contains("SackGhostRewind.register()"));
        assertTrue(items.contains("maintainWards(level, round)"));
    }

    @Test void echoReturnBypassesItsCooldownWithoutStartingAnotherOne() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        String use = role.substring(role.indexOf("public boolean useArt("), role.indexOf("public double drainSan("));
        assertTrue(use.indexOf("return returnEcho(owner)") < use.indexOf("int remaining = artCooldown(art)"));
        assertTrue(use.contains("artReadyAt.put(art, now() + SackGhostRules.ART_COOLDOWN)"));
        assertFalse(role.contains("castReadyAt"));
        String returning = role.substring(role.indexOf("private boolean returnEcho("), role.indexOf("public void onEffigyHit("));
        assertFalse(returning.contains("artReadyAt.put"));
        assertTrue(returning.contains("finishEcho(owner)"));
        String items = source("game/roles/killer/ma_chen_xu/SackGhostItems.java");
        assertTrue(items.contains("spentArts.add(token)"));
    }

    @Test void parasiteAndBodyUseShieldAwareDeathInsteadOfForcedDeath() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        String parasite = role.substring(role.indexOf("private void executeParasite("), role.indexOf("private boolean castSeize("));
        assertTrue(parasite.indexOf("parasites.remove") < parasite.indexOf("GameUtils.killPlayer("));
        assertFalse(parasite.contains("forceKillPlayer"));
        String body = role.substring(role.indexOf("public void onBodyHit("), role.indexOf("private boolean tryEscape("));
        assertTrue(body.contains("GameUtils.killPlayer("));
        assertFalse(body.contains("forceKillPlayer"));
        assertTrue(body.contains("bodyDeathCheck = true"));
        assertTrue(body.contains("bodyDeathCheck = false"));
    }

    @Test void escapeStaysInPlaceAndCancelsRatherThanExecutesRealmMarks() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        String escape = role.substring(role.indexOf("private boolean tryEscape("), role.indexOf("private void endEscape("));
        assertTrue(escape.contains("endOtherworld(false)"));
        assertTrue(escape.contains("SackGhostRules.ESCAPE_DURATION"));
        assertFalse(escape.contains("teleport"));
        String events = source("game/roles/killer/ma_chen_xu/MaChenXuEventHandler.java");
        assertTrue(events.contains("AfterShieldAllowPlayerDeathWithKiller.EVENT.register(ESCAPE_PHASE"));
        assertTrue(events.contains("clone_substitute_after_shields"));
    }

    @Test void temporaryGhostAndControlEffectsAreFilteredFromRewind() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        assertTrue(role.contains("state.putBoolean(\"Invulnerable\", escapePreviousInvulnerable)"));
        assertTrue(role.contains("effects.putAll(formEffects)"));
        assertTrue(role.contains("escapeReadyAt = Math.max(escapeReadyAt, liveEscapeReadyAt)"));
        assertTrue(source("game/roles/killer/ma_chen_xu/SackGhostRewind.java").contains("SackGhostActions.filterRewindSnapshot(player, state)"));
        String config = Files.readString(Path.of("src/main/resources/noellesroles.mixins.json"));
        assertTrue(config.contains("client.roles.ma_chen_xu.SackGhostPlayerRenderMixin"));
        assertTrue(config.contains("client.roles.ma_chen_xu.SackGhostHandRenderMixin"));
    }

    @Test void independentCooldownsSyncAndCannotBeResetByRewind() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        assertTrue(role.contains("artReadyAt.getOrDefault(art, 0L)"));
        assertTrue(role.contains("artCooldowns.putInt(art.id, artCooldown(art))"));
        assertTrue(role.contains("artCooldowns.getInt(art.id)"));
        assertTrue(role.contains("artReadyAt.merge(art, readyAt, Math::max)"));
        String client = source("client/SackGhostClient.java");
        assertTrue(client.contains("data.artCooldown(art)"));
        assertFalse(client.contains("data.castCooldown"));
    }

    @Test void onlySuccessfulCastsRollConsumptionAndEchoRollsOnce() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        String use = role.substring(role.indexOf("public boolean useArt("), role.indexOf("public double drainSan("));
        assertTrue(use.indexOf("if (!used) return false") < use.indexOf("owner.getRandom().nextBoolean()"));
        assertTrue(use.contains("art != GhostArt.ECHO && SackGhostRules.consumesOnCast"));
        String echo = role.substring(role.indexOf("private boolean castEcho("), role.indexOf("private boolean collisionFree("));
        assertTrue(echo.indexOf("if (destination == null)") < echo.indexOf("owner.getRandom().nextBoolean()"));
        assertEquals(1, echo.split("nextBoolean", -1).length - 1);
        String returning = role.substring(role.indexOf("private boolean returnEcho("), role.indexOf("public void onEffigyHit("));
        assertFalse(returning.contains("nextBoolean"));
    }

    @Test void realmPressureUsesEnemyRangeAndRestorableLeases() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        String pressure = role.substring(role.indexOf("private void tickRealmPressure("), role.indexOf("private void lease("));
        assertTrue(pressure.contains("isEnemy(target)"));
        assertTrue(pressure.contains("inRealmPressureRange(owner.distanceToSqr(target))"));
        assertTrue(pressure.contains("lease(target, MobEffects.BLINDNESS"));
        assertTrue(pressure.contains("lease(target, ModEffects.TREMBLE"));
        assertTrue(pressure.contains("lease(target, ModEffects.LIMP"));
        assertTrue(pressure.contains("restoreLease(target, effect"));
        String tick = role.substring(role.indexOf("@Override public void serverTick()"), role.indexOf("@Override public void clientTick()"));
        assertTrue(tick.contains("if (otherworldActive) {\n            if (clockRunning() && now() % 5 == 0) tickRealmPressure(owner)"));
        assertTrue(role.contains("SackGhostRules.boostedSanDrain(points, otherworldActive)"));
    }
}
