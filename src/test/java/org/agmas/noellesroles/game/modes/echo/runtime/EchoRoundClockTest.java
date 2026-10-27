package org.agmas.noellesroles.game.modes.echo.runtime;

import org.agmas.noellesroles.game.modes.echo.state.EchoPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EchoRoundClockTest {
    @Test void safeThenFullEvacuationOnLastOperationTick() {
        EchoRoundClock clock = new EchoRoundClock();
        clock.capture(0, 1, false);
        assertEquals(EchoRoundClock.Boundary.OPERATIONS_STARTED, clock.finish(0, false));
        clock.capture(1, 1, false);
        assertTrue(clock.openEvacuation(false));
        assertEquals(EchoRoundClock.Boundary.NONE, clock.finish(1800, false));
        assertEquals(EchoPhase.EVACUATION, clock.phase());
        assertEquals(2, clock.matchTick());
    }

    @Test void pauseAndDuplicateEndNeverAdvanceTwice() {
        EchoRoundClock clock = new EchoRoundClock();
        for (int n = 0; n < 200; n++) {
            clock.capture(n, 100, true);
            assertFalse(clock.canProcess(false));
            assertEquals(EchoRoundClock.Boundary.NONE, clock.finish(100, false));
        }
        assertEquals(0, clock.matchTick());
        clock.capture(200, 100, false);
        clock.finish(99, false);
        clock.finish(99, false);
        assertEquals(1, clock.matchTick());
        assertThrows(IllegalStateException.class, () -> clock.capture(200, 99, false));
    }

    @Test void zeroAtStartCannotCompleteTargetsAndSettlementStopsClock() {
        EchoRoundClock clock = new EchoRoundClock();
        clock.capture(0, 1, false);
        clock.finish(0, false);
        clock.capture(1, 0, false);
        assertFalse(clock.openEvacuation(false));
        assertEquals(EchoRoundClock.Boundary.TIME_EXPIRED, clock.finish(0, false));
        clock.settle();
        clock.capture(2, 20, false);
        assertFalse(clock.canProcess(false));
        clock.finish(19, false);
        assertEquals(1, clock.matchTick());
    }
}
