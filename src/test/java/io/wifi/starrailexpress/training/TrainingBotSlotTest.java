package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainingBotSlotTest {
    @Test
    void reservedNamesAreValidUniqueAndKeepTheSlotAcrossAttempts() {
        var names = new HashSet<String>();
        for (int slot = 0; slot < 32; slot++) {
            for (int serial : new int[]{0, 1, 35, 36, 36 * 36 * 36 * 36 - 1}) {
                String name = TrainingBotSlot.name("abcdef", slot, serial);
                assertEquals(16, name.length());
                assertTrue(name.matches("[A-Za-z0-9_]+"));
                assertEquals(name.toLowerCase(java.util.Locale.ROOT), name);
                assertEquals(String.format(java.util.Locale.ROOT, "%02d", slot), name.substring(10, 12));
                assertTrue(names.add(name));
            }
        }
        assertNotEquals(TrainingBotSlot.name("abcdef", 0, 0), TrainingBotSlot.name("012345", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> TrainingBotSlot.name("abcdef", 32, 0));
        assertThrows(IllegalArgumentException.class, () -> TrainingBotSlot.name("abcdef", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> TrainingBotSlot.name("bad", 0, 0));
    }

    @Test
    void decreaseOrResetCancelsPendingWithoutAcceptingLateCompletion() {
        var old = new TrainingBotSlot(2, 4);
        var request = old.begin("_SRTabcdef020000", 0);
        request.submitted = true;
        assertSame(request, old.cancel());
        assertTrue(request.cancelled);
        assertFalse(old.accept(request, UUID.randomUUID(), 4));
        assertFalse(old.canCreate(1000));
        assertNull(old.cancel());
        var replacement = new TrainingBotSlot(2, 5);
        var next = replacement.begin("_SRTabcdef020001", 1);
        assertFalse(replacement.accept(request, UUID.randomUUID(), 5));
        assertFalse(replacement.accept(next, UUID.randomUUID(), 4));
        UUID id = UUID.randomUUID();
        assertTrue(replacement.accept(next, id, 5));
        assertEquals(id, replacement.uuid);
        assertNull(replacement.pending);
        assertFalse(replacement.canCreate(1000));
        assertFalse(replacement.accept(next, UUID.randomUUID(), 5));
    }

    @Test
    void timeoutAndBackoffAreBoundedAndManualRetryIsExplicit() {
        var slot = new TrainingBotSlot(0, 1);
        long now = 0;
        for (int i = 1; i <= TrainingBotSlot.MAX_ATTEMPTS; i++) {
            var request = slot.begin("request" + i, now);
            assertFalse(slot.timedOut(now + TrainingBotSlot.CREATE_TIMEOUT - 1));
            assertTrue(slot.timedOut(now + TrainingBotSlot.CREATE_TIMEOUT));
            now += TrainingBotSlot.CREATE_TIMEOUT;
            assertSame(request, slot.fail("timeout", now));
            assertTrue(request.cancelled);
            assertEquals("timeout", slot.error);
            assertFalse(slot.canCreate(now + 40L * i - 1));
            now += 40L * i;
        }
        assertFalse(slot.canCreate(now + 100000));
        slot.retry(now);
        assertEquals(0, slot.attempts);
        assertEquals("", slot.error);
        assertTrue(slot.canCreate(now));
    }

    @Test
    void cancelledAsyncReservationCannotBeForgottenBeforeTheLateFakeIsObserved() {
        var slot = new TrainingBotSlot(0, 8);
        var request = slot.begin("_SRTabcdef000001", 0);
        request.submitted = true;
        slot.retry(250);
        assertTrue(request.cancelled);
        assertFalse(request.canForget(false));
        UUID owned = UUID.randomUUID();
        assertTrue(request.matches(request.name, owned, true));
        assertFalse(request.matches("unrelated_fake", owned, true));
        assertFalse(request.matches(request.name, owned, false));
        request.uuid = owned;
        request.observed = true;
        assertFalse(request.matches(request.name, UUID.randomUUID(), true));
        assertFalse(request.canForget(true));
        assertTrue(request.canForget(false));
    }

    @Test
    void rejectedSubmissionCanBeDroppedButNeverTargetsAnyPlayer() {
        var slot = new TrainingBotSlot(1, 2);
        var request = slot.begin("_SRTabcdef010000", 0);
        slot.fail("rejected", 0);
        assertTrue(request.canForget(false));
        assertFalse(request.matches(request.name, UUID.randomUUID(), true));
    }
    @Test
    void disconnectedOwnedSlotCanBeRecreatedWithFreshIdentityWithoutAcceptingOldCallbacks() {
        var slot = new TrainingBotSlot(3, 9);
        var old = slot.begin(TrainingBotSlot.name("abcdef", 3, 0), 0);
        old.submitted = true;
        UUID oldId = UUID.randomUUID();
        assertTrue(slot.accept(old, oldId, 9));
        old.cancelled = true; // 调用方先撤销已建立连接的精确保留项。
        assertNull(slot.retry(80));
        assertNull(slot.uuid);
        assertEquals(0, slot.attempts);
        assertTrue(slot.canCreate(80));
        var next = slot.begin(TrainingBotSlot.name("abcdef", 3, 1), 80);
        assertNotEquals(old.name, next.name);
        assertFalse(slot.accept(old, oldId, 9));
        UUID newId = UUID.randomUUID();
        assertTrue(slot.accept(next, newId, 9));
        assertEquals(newId, slot.uuid);
        assertFalse(old.matches(next.name, newId, true));
    }

    @Test
    void retryCannotResurrectRetiredSlot() {
        var slot = new TrainingBotSlot(0, 1);
        slot.cancel();
        slot.retry(200);
        assertFalse(slot.canCreate(200));
    }
    @Test
    void normalizedProfileNamesRemainOwnedForOnlineLookupAndRetirement() {
        var slot = new TrainingBotSlot(0, 1);
        var request = slot.begin("_SRTabcdef000001", 0);
        request.submitted = true;
        UUID id = UUID.randomUUID();
        assertTrue(slot.accept(request, id, 1));
        assertTrue(request.matches("_srtabcdef000001", id, true));
        assertTrue(request.matches("_SrTaBcDeF000001", id, true));
        request.cancelled = true;
        assertTrue(request.matches("_srtabcdef000001", id, true));
        assertFalse(request.matches("_srtabcdef000001", UUID.randomUUID(), true));
        assertFalse(request.matches("_srtabcdef000001", id, false));
        assertFalse(request.matches("_srtabcdef000002", id, true));
        assertFalse(request.canForget(true));
        assertTrue(request.canForget(false));
    }

    @Test
    void cancelledUnobservedMixedCaseCallbackCanBeReclaimedWithoutTargetingAnotherFake() {
        var slot = new TrainingBotSlot(1, 1);
        var request = slot.begin("_SRTabcdef010000", 0);
        request.submitted = true;
        slot.retry(250);
        UUID id = UUID.randomUUID();
        assertTrue(request.matches("_srtabcdef010000", id, true));
        request.uuid = id;
        request.observed = true;
        assertFalse(request.matches("_srtabcdef010001", id, true));
        assertFalse(request.matches("_srtabcdef010000", UUID.randomUUID(), true));
        assertFalse(slot.accept(request, id, 1));
    }

    @Test
    void normalizedAliveProfilesStayInTheirFiveSlotsWithoutReplacementCreates() {
        var slots = new TrainingBotSlot[5];
        var requests = new TrainingBotSlot.Attempt[5];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new TrainingBotSlot(i, 1);
            requests[i] = slots[i].begin(TrainingBotSlot.name("abcdef", i, i), 0);
            requests[i].submitted = true;
            assertTrue(slots[i].accept(requests[i], UUID.randomUUID(), 1));
        }
        for (int tick = 1; tick <= 2000; tick++) {
            for (int i = 0; i < slots.length; i++) {
                assertTrue(requests[i].matches(requests[i].name.toUpperCase(java.util.Locale.ROOT), slots[i].uuid, true));
                assertFalse(slots[i].canCreate(tick));
                assertEquals(1, slots[i].attempts);
            }
        }
    }

    @Test
    void configurationErrorsCannotTriggerAnAutomaticRespawnLoop() {
        var slot = new TrainingBotSlot(0, 1);
        var request = slot.begin("_srtabcdef000001", 0);
        request.submitted = true;
        assertFalse(slot.canAutoRespawn(true, false));
        assertTrue(slot.accept(request, UUID.randomUUID(), 1));
        assertFalse(slot.canAutoRespawn(true, false));
        assertTrue(slot.canAutoRespawn(true, true));
        assertFalse(slot.canAutoRespawn(false, true));
        slot.error = "training.error.bot_config_failed";
        assertFalse(slot.canAutoRespawn(true, true));
        slot.error = "";
        slot.cancel();
        assertFalse(slot.canAutoRespawn(true, true));
    }
    @Test
    void repeatedInPlaceRevivalsKeepTheSameIdentityReservationAndSpawnCount() {
        var slot = new TrainingBotSlot(2, 7);
        var reservation = slot.begin(TrainingBotSlot.name("abcdef", 2, 0), 0);
        reservation.submitted = true;
        UUID id = UUID.randomUUID();
        assertTrue(slot.accept(reservation, id, 7));
        for (int round = 0; round < 100; round++) {
            slot.error = "training.error.bot_revive_failed";
            assertTrue(slot.revived(reservation, id, 7));
            assertEquals(id, slot.uuid);
            assertEquals(id, reservation.uuid);
            assertFalse(reservation.cancelled);
            assertTrue(reservation.matches(reservation.name, id, true));
            assertEquals(1, slot.attempts);
            assertEquals("", slot.error);
            assertNull(slot.pending);
            assertFalse(slot.canCreate(1000L * round));
        }
    }

    @Test
    void inPlaceRevivalRejectsChangedIdentityStaleGenerationAndCancelledReservation() {
        var slot = new TrainingBotSlot(1, 4);
        var reservation = slot.begin(TrainingBotSlot.name("abcdef", 1, 0), 0);
        reservation.submitted = true;
        UUID id = UUID.randomUUID();
        assertTrue(slot.accept(reservation, id, 4));
        slot.error = "keep_error";
        assertFalse(slot.revived(reservation, UUID.randomUUID(), 4));
        assertFalse(slot.revived(reservation, id, 5));
        assertFalse(slot.revived(null, id, 4));
        reservation.cancelled = true;
        assertFalse(slot.revived(reservation, id, 4));
        reservation.cancelled = false;
        reservation.uuid = UUID.randomUUID();
        assertFalse(slot.revived(reservation, id, 4));
        assertEquals(id, slot.uuid);
        assertEquals("keep_error", slot.error);
    }

    @Test
    void pendingOrRetiredSlotsCannotBeRevivedInPlace() {
        var slot = new TrainingBotSlot(0, 3);
        var reservation = slot.begin(TrainingBotSlot.name("abcdef", 0, 0), 0);
        reservation.submitted = true;
        UUID id = UUID.randomUUID();
        assertFalse(slot.revived(reservation, id, 3));
        assertTrue(slot.accept(reservation, id, 3));
        reservation.observed = false;
        assertFalse(slot.revived(reservation, id, 3));
        reservation.observed = true;
        assertTrue(slot.revived(reservation, id, 3));
        slot.cancel();
        assertFalse(slot.revived(reservation, id, 3));
        assertFalse(slot.canCreate(1000));
    }
    @Test
    void asyncProfileCanSubmitOnlyForCurrentUncancelledUnsubmittedAttempt() {
        var slot = new TrainingBotSlot(0, 5);
        var attempt = slot.begin(TrainingBotSlot.name("abcdef", 0, 0), 0);
        assertTrue(slot.canSubmit(attempt, 5));
        assertFalse(slot.canSubmit(attempt, 6));
        assertFalse(slot.canSubmit(null, 5));
        attempt.submitted = true;
        assertFalse(slot.canSubmit(attempt, 5));
        attempt.submitted = false;
        slot.retry(20);
        assertFalse(slot.canSubmit(attempt, 5));
        assertTrue(attempt.canForget(false));
        var next = slot.begin(TrainingBotSlot.name("abcdef", 0, 1), 20);
        assertTrue(slot.canSubmit(next, 5));
        assertFalse(slot.canSubmit(attempt, 5));
        slot.fail("timeout", 220);
        assertFalse(slot.canSubmit(next, 5));
        assertTrue(next.canForget(false));
    }

    @Test
    void leavingOrResizingWhileProfileIsPendingCannotSpawnALateFake() {
        var slot = new TrainingBotSlot(2, 7);
        var attempt = slot.begin(TrainingBotSlot.name("abcdef", 2, 0), 0);
        assertTrue(slot.canSubmit(attempt, 7));
        slot.cancel();
        assertFalse(slot.canSubmit(attempt, 7));
        assertTrue(attempt.canForget(false));
        assertFalse(attempt.matches(attempt.name, UUID.randomUUID(), true));
    }
}
