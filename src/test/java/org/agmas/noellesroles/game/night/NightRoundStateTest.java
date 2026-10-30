package org.agmas.noellesroles.game.night;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NightRoundStateTest {
    @Test void zeroChanceNeverStartsNaturally() {
        NightRoundState state = new NightRoundState();
        for (int roll = 0; roll < 10000; roll++) assertFalse(state.prepare(true, 0, roll));
        assertFalse(state.prepare(true, -10, 0));
    }

    @Test void forcedNextIsConsumedOnceAndDoesNotDependOnChance() {
        NightRoundState state = new NightRoundState();
        assertTrue(state.requestNext());
        assertFalse(state.requestNext());
        assertTrue(state.prepare(true, 0, 9999));
        assertFalse(state.isForcePending());
        assertFalse(state.prepare(true, 0, 0));
    }

    @Test void unsupportedRoundAndCurrentRoundCleanupPreserveQueuedNext() {
        NightRoundState state = new NightRoundState();
        assertTrue(state.requestNext());
        assertFalse(state.prepare(false, 10000, 0));
        state.clearRound();
        assertTrue(state.isForcePending());
        assertTrue(state.prepare(true, 0, 9999));
    }

    @Test void configurableChanceHasExactBoundaries() {
        NightRoundState state = new NightRoundState();
        assertTrue(state.prepare(true, 20, 19));
        assertFalse(state.prepare(true, 20, 20));
        assertTrue(state.prepare(true, 10000, 9999));
        assertTrue(state.prepare(true, Integer.MAX_VALUE, 9999));
    }

    @Test void flashlightIsOncePerRoundAcrossReconnectButFailedDeliveryCanRetry() {
        NightRoundState state = new NightRoundState();
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(state.equip(player));
        assertFalse(state.equip(player));
        assertTrue(state.equip(other));
        state.retryEquip(player);
        assertTrue(state.equip(player));
        state.clearRound();
        assertTrue(state.equip(player));
    }

    @Test void deathCallbacksDeduplicateUntilActualRevival() {
        NightRoundState state = new NightRoundState();
        UUID victim = UUID.randomUUID();
        assertTrue(state.markDead(victim));
        assertFalse(state.markDead(victim));
        assertFalse(state.markDead(victim));
        state.observeAlive(victim);
        assertTrue(state.markDead(victim));
        state.clearRound();
        assertTrue(state.markDead(victim));
    }

    @Test void fogIsSphericalSixteenBlockCapWithoutWideningOtherEffects() {
        for (float existing : new float[]{16, 17, 32, 256, Float.POSITIVE_INFINITY, Float.NaN, -1, 0}) {
            assertEquals(16, NightRoundState.fogEnd(existing));
        }
        assertEquals(2, NightRoundState.fogEnd(2));
        assertEquals(7, NightRoundState.fogEnd(7));
        assertEquals(14, NightRoundState.fogStart(100, 16));
        assertEquals(0, NightRoundState.fogStart(-2, 16));
        assertEquals(14, NightRoundState.fogStart(Float.NaN, 16));
        assertEquals(0, NightRoundState.fogStart(5, 2));
    }
}
