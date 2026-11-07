package org.agmas.noellesroles.game.roles.killer.clone_doctor;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloneDoctorTest {
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
    void audibleCastDoesNotStartCopyCooldownUntilSuccess() {
        var timers = new CloneDoctorTimers();
        timers.beginCast(100);
        assertEquals(120, timers.castAt);
        assertEquals(0, timers.copyReadyAt);
        timers.completeCopy(120);
        assertEquals(0, timers.castAt);
        assertEquals(1320, timers.endsAt);
        assertEquals(320, timers.invisibleUntil);
        assertEquals(1020, timers.copyReadyAt);
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
    void earlyReturnKeepsCopyCooldownAndFullNoDamageWindow() {
        var timers = new CloneDoctorTimers();
        timers.completeCopy(100);
        timers.stopCopy();
        assertEquals(0, timers.endsAt);
        assertEquals(900, CloneDoctorTimers.remaining(timers.copyReadyAt, 100));
        assertEquals(200, CloneDoctorTimers.remaining(timers.invisibleUntil, 100));
        assertEquals(0, CloneDoctorTimers.remaining(timers.copyReadyAt, 1000));
    }

    @Test
    void fullTransformationOutlastsCopyCooldown() {
        var timers = new CloneDoctorTimers();
        timers.completeCopy(0);
        assertEquals(0, CloneDoctorTimers.remaining(timers.copyReadyAt, 900));
        assertEquals(300, CloneDoctorTimers.remaining(timers.endsAt, 900));
        timers.stopCopy();
        assertEquals(0, CloneDoctorTimers.remaining(timers.copyReadyAt, 1200));
    }

    @Test
    void substituteAndSlimeCooldownsAreIndependentAndSurviveReturn() {
        var timers = new CloneDoctorTimers();
        timers.substituteCreated(100);
        timers.slimeBought(100);
        timers.completeCopy(200);
        timers.stopCopy();
        assertEquals(10100, timers.substituteReadyAt);
        assertEquals(2500, timers.slimeReadyAt);
        assertEquals(1100, timers.copyReadyAt);
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
