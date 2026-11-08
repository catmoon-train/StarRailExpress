package org.agmas.noellesroles.game.roles.killer.clone_doctor;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloneDoctorTest {
    @Test
    void rainbowCyclesWithoutTurningHiddenTargetsVisibleOrChangingOtherPlayers() {
        int first = CloneDoctorOutlineColor.rainbow(0);
        assertNotEquals(first, CloneDoctorOutlineColor.rainbow(1250));
        assertNotEquals(first, CloneDoctorOutlineColor.rainbow(2500));
        assertEquals(first, CloneDoctorOutlineColor.rainbow(5000));
        assertEquals(255, first >>> 24);
        assertTrue(CloneDoctorOutlineColor.recolor(java.util.OptionalInt.empty(), true, 0).isEmpty());
        var ordinary = java.util.OptionalInt.of(0x123456);
        assertEquals(ordinary, CloneDoctorOutlineColor.recolor(ordinary, false, 1250));
        assertEquals(java.util.OptionalInt.of(CloneDoctorOutlineColor.rainbow(1250)),
                CloneDoctorOutlineColor.recolor(ordinary, true, 1250));
    }

    @Test
    void transformedDoctorRemainsCountedAsKillerAndNotAsCivilian() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/wifi/starrailexpress/cca/SREGameWorldComponent.java"));
        for (String signature : List.of("public boolean canIncreaseSurvivingKillers(ServerPlayer player)",
                "public boolean canIncreaseSurvivingInnocents(Player player)",
                "private boolean isDualCountDeadlock(SRERole role)", "public boolean isKillerTeam(UUID player)",
                "public AlivePlayerRoleTeamInfo getAlivePlayerRoleTeamInfo()")) {
            int start = source.indexOf(signature);
            assertTrue(start >= 0, signature);
            String method = source.substring(start, source.indexOf("\n    }", start));
            assertTrue(method.contains("getAllegianceRole("), signature);
            assertFalse(method.contains("getRole("), signature);
        }
        String role = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/roles/killer/clone_doctor/CloneDoctorRole.java"));
        assertTrue(role.contains("isClone(level, playerId) ? ModRoles.CLONE_DOCTOR : null"));
        String mode = Files.readString(Path.of("src/main/java/io/wifi/starrailexpress/game/modes/SREMurderGameMode.java"));
        assertTrue(mode.contains("gameWorldComponent.canIncreaseSurvivingKillers(player)"));
    }

    @Test
    void bothCachedAndDirectOutlinePathsKeepDoctorRainbowDynamicAfterRoleChange() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/wifi/starrailexpress/client/SREClient.java"))
                .replace("\r\n", "\n");
        assertTrue(source.contains("CloneDoctorClient.applyOutlineColor(target, cached)"));
        assertTrue(source.contains("CloneDoctorClient.applyOutlineColor(target,\n                InstinctManager.getInstinctHighlight(target))"));
        String client = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/client/CloneDoctorClient.java"));
        assertTrue(client.contains("CloneDoctorRole.isClone(player)"));
        assertTrue(client.contains("net.minecraft.Util.getMillis()"));
        assertTrue(client.indexOf("if (permittedColor.isEmpty()) return permittedColor;")
                < client.indexOf("CloneDoctorRole.isClone(player)"));
    }

    @Test
    void transformedHudShowsAbilityRoleAndReturnTimeAndRestorationUsesSubtitleHud() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles");
        String client = Files.readString(root.resolve("client/CloneDoctorClient.java"));
        assertTrue(client.contains("if (state.transformTicks() > 0) y = renderTransformationStatus"));
        assertTrue(client.contains("SREClient.gameComponent.getRole(client.player)"));
        assertTrue(client.contains("RoleUtils.getRoleName(role)"));
        assertTrue(client.contains("KEY + \"current_role\", roleName"));
        assertTrue(client.contains("KEY + \"return_in\", (state.transformTicks() + 19) / 20"));
        String server = Files.readString(root.resolve("game/roles/killer/clone_doctor/CloneDoctorSessionManager.java"));
        int returnAt = server.indexOf("public static void returnToSelf");
        int nextMethod = server.indexOf("public static void beforeRoleChange", returnAt);
        String returning = server.substring(returnAt, nextMethod);
        assertTrue(returning.indexOf("switchRole(player, state, ModRoles.CLONE_DOCTOR, false)")
                < returning.indexOf("sendCODSubtitleToPlayerTop(player,"));
        assertTrue(returning.contains("state.stopCopy(now(player))"));
        assertTrue(returning.indexOf("state.stopCopy(now(player))") < returning.indexOf("sendCODSubtitleToPlayerTop(player,"));
        assertTrue(returning.contains("!player.hasDisconnected()"));
        assertTrue(returning.contains("GameUtils.isPlayerAliveAndSurvival(player)"));
        for (String locale : List.of("zh_cn", "zh_tw", "en_us")) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/noellesroles/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : List.of("hud.noellesroles.clone_doctor.current_role",
                    "hud.noellesroles.clone_doctor.return_in", "message.noellesroles.clone_doctor.returned",
                    "message.noellesroles.clone_doctor.returned_detail")) assertTrue(language.has(key), locale + ": " + key);
        }
    }

    @Test
    void clientInitializationWiresStateReturnButtonAndHud() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles");
        String initializer = Files.readString(root.resolve("client/NoellesrolesClient.java"));
        assertEquals(1, initializer.split("CloneDoctorClient\\.register\\(\\);", -1).length - 1);
        String client = Files.readString(root.resolve("client/CloneDoctorClient.java"));
        assertTrue(client.contains("if (registered) return;"));
        assertTrue(client.contains("ClientPlayNetworking.registerGlobalReceiver(CloneDoctorStateS2CPacket.ID"));
        assertTrue(client.contains("LimitedInventoryScreenEvents.INIT_TAIL.register"));
        assertTrue(client.contains("HudRenderCallback.EVENT.register"));
    }

    @Test
    void crowdedInventoryNeverHidesReturnAndCastGetsVisibleProgress() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles");
        String client = Files.readString(root.resolve("client/CloneDoctorClient.java"));
        int positioning = client.indexOf("private static void positionReturnButton");
        String layout = client.substring(positioning, client.indexOf("private static void tick", positioning));
        assertFalse(layout.contains("returnButton.visible = false"));
        assertFalse(layout.contains("returnButton.active = false"));
        assertTrue(layout.contains("(screen.width - returnButton.getWidth()) / 2"));
        assertTrue(layout.contains("screen.height - returnButton.getHeight() - 6"));
        assertFalse(layout.contains("new int[]"));
        assertTrue(client.contains("if (state.castTicks() > 0) renderCastProgress(graphics)"));
        assertTrue(client.contains("graphics.drawCenteredString(font, label"));
        String server = Files.readString(root.resolve("game/roles/killer/clone_doctor/CloneDoctorSessionManager.java"));
        assertTrue(server.contains("message.noellesroles.clone_doctor.casting"));
        for (String locale : List.of("zh_cn", "zh_tw", "en_us")) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/noellesroles/lang/" + locale + ".json"))).getAsJsonObject();
            assertTrue(language.has("message.noellesroles.clone_doctor.casting"), locale);
        }
    }

    @Test
    void zeroCooldownUsesReadyLabelInsteadOfZeroSeconds() throws Exception {
        String client = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/client/CloneDoctorClient.java"));
        assertTrue(client.contains("Component text = ticks <= 0"));
        assertTrue(client.contains("key.replace(\"_cooldown\", \"_ready\")"));
        for (String locale : List.of("zh_cn", "zh_tw", "en_us")) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/noellesroles/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : List.of("hud.noellesroles.clone_doctor.copy_ready",
                    "hud.noellesroles.clone_doctor.substitute_ready")) {
                assertTrue(language.has(key), locale + ": " + key);
                assertFalse(language.get(key).getAsString().contains("%s"));
            }
        }
    }

    @Test
    void pendingItemsUseVisibleSlotsAndAreClearedBeforeReturning() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/roles/killer/clone_doctor/CloneDoctorSessionManager.java"));
        assertFalse(source.contains("player.getInventory().add(stack)"));
        assertTrue(source.contains("slot < 9"));
        assertTrue(source.contains("capacity >= stack.getCount() && RoleUtils.insertStackInFreeSlot"));
        int returnAt = source.indexOf("public static void returnToSelf");
        assertTrue(source.indexOf("state.pendingItems.clear()", returnAt)
                < source.indexOf("switchRole(player, state, ModRoles.CLONE_DOCTOR, false)", returnAt));
        assertTrue(source.contains("state.copySession.equals(tag.getUUID(TEMP_SESSION))"));
    }

    @Test
    void audibleCastAndSuccessfulTransformationDoNotStartReturnCooldown() {
        var timers = new CloneDoctorTimers();
        timers.beginCast(100);
        assertEquals(120, timers.castAt);
        assertEquals(0, timers.copyReadyAt);
        timers.completeCopy(120);
        assertEquals(0, timers.castAt);
        assertEquals(1320, timers.endsAt);
        assertEquals(320, timers.invisibleUntil);
        assertEquals(0, timers.copyReadyAt);
    }

    @Test
    void interruptedCastDoesNotConsumeCooldown() {
        var timers = new CloneDoctorTimers();
        timers.beginCast(50);
        timers.cancelCast();
        assertEquals(0, timers.castAt);
        assertEquals(0, timers.copyReadyAt);
        assertEquals(0, timers.endsAt);
    }

    @Test
    void earlyReturnStartsThirtySecondCooldownAndPreservesNoDamageWindow() {
        var timers = new CloneDoctorTimers();
        timers.completeCopy(100);
        timers.stopCopy(110);
        assertEquals(0, timers.endsAt);
        assertEquals(600, CloneDoctorTimers.remaining(timers.copyReadyAt, 110));
        assertEquals(190, CloneDoctorTimers.remaining(timers.invisibleUntil, 110));
        assertEquals(1, CloneDoctorTimers.remaining(timers.copyReadyAt, 709));
        assertEquals(0, CloneDoctorTimers.remaining(timers.copyReadyAt, 710));
    }

    @Test
    void fullTransformationStartsFullThirtySecondsOnlyAfterReturning() {
        var timers = new CloneDoctorTimers();
        timers.completeCopy(0);
        assertEquals(0, CloneDoctorTimers.remaining(timers.copyReadyAt, 900));
        assertEquals(300, CloneDoctorTimers.remaining(timers.endsAt, 900));
        timers.stopCopy(1200);
        assertEquals(600, CloneDoctorTimers.remaining(timers.copyReadyAt, 1200));
        assertEquals(0, CloneDoctorTimers.remaining(timers.copyReadyAt, 1800));
    }

    @Test
    void substituteAndSlimeCooldownsAreIndependentAndSurviveReturn() {
        var timers = new CloneDoctorTimers();
        timers.substituteCreated(100);
        timers.slimeBought(100);
        timers.completeCopy(200);
        timers.stopCopy(300);
        assertEquals(10100, timers.substituteReadyAt);
        assertEquals(2500, timers.slimeReadyAt);
        assertEquals(900, timers.copyReadyAt);
    }

    @Test
    void timersArePerPlayerAndPausedClockDoesNotAdvanceThem() {
        var first = new CloneDoctorTimers();
        var second = new CloneDoctorTimers();
        first.completeCopy(500);
        assertEquals(0, second.endsAt);
        int paused = CloneDoctorTimers.remaining(first.endsAt, 500);
        assertEquals(paused, CloneDoctorTimers.remaining(first.endsAt, 500));
        assertEquals(paused - 20, CloneDoctorTimers.remaining(first.endsAt, 520));
    }

    @Test
    void remainingClampsExpiredAndLargeValues() {
        assertEquals(0, CloneDoctorTimers.remaining(100, 100));
        assertEquals(0, CloneDoctorTimers.remaining(100, 101));
        assertEquals(Integer.MAX_VALUE, CloneDoctorTimers.remaining(3_000_000_000L, 0));
    }

    @Test
    void translationsAndCommonSideMixinRegistrationsAreComplete() throws Exception {
        Path assets = Path.of("src/main/resources/assets");
        for (String locale : List.of("zh_cn", "zh_tw", "en_us")) {
            var role = JsonParser.parseString(Files.readString(assets.resolve("noellesroles/lang/" + locale + ".json")))
                    .getAsJsonObject();
            for (String key : List.of("announcement.star.role.clone_doctor", "announcement.star.goals.clone_doctor",
                    "announcement.star.modifier.noellesroles.cloned_person", "hud.noellesroles.clone_doctor.return")) {
                assertTrue(role.has(key), locale + ": " + key);
            }
            var intro = JsonParser.parseString(Files.readString(assets.resolve("role_modifier_intro/lang/" + locale + ".json")))
                    .getAsJsonObject();
            assertTrue(intro.has("info.screen.roleid.clone_doctor"));
            assertTrue(intro.has("info.screen.modifier.cloned_person"));
        }
        var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/noellesroles.mixins.json")))
                .getAsJsonObject().getAsJsonArray("mixins").asList();
        for (String mixin : List.of("roles.clone_doctor.CloneDoctorItemMixin", "roles.clone_doctor.CloneDoctorInventoryMixin")) {
            assertTrue(mixins.stream().anyMatch(value -> value.getAsString().equals(mixin)));
        }
    }

    @Test
    void substituteUsesPlayerPuppetAndOnlyConsumesAfterShieldAndValidation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/roles/killer/clone_doctor/CloneSubstituteManager.java"));
        assertTrue(source.contains("ModEntities.PUPPETEER_BODY.create(level)"));
        assertTrue(source.contains("AfterShieldAllowPlayerDeathWithKiller.EVENT.register"));
        assertTrue(source.indexOf("GameUtils.isForcedDeathInProgress()") < source.indexOf("SUBSTITUTES.remove(target.getUUID())"));
        assertTrue(source.indexOf("if (!isValid(substitute))") < source.indexOf("SUBSTITUTES.remove(target.getUUID())"));
        assertTrue(source.contains("bed == null ? roomPosition"));
    }
}
