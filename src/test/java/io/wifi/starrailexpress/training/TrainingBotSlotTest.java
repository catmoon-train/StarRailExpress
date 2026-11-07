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
}
