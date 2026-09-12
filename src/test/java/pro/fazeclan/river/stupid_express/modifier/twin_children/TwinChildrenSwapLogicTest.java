package pro.fazeclan.river.stupid_express.modifier.twin_children;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwinChildrenSwapLogicTest {

    @Test
    void intervalIsSixtySeconds() {
        assertEquals(1200, TwinChildrenSwapLogic.intervalTicks());
    }

    @Test
    void swapIsDueAtOrAfterScheduledTick() {
        assertFalse(TwinChildrenSwapLogic.due(1199, 1200));
        assertTrue(TwinChildrenSwapLogic.due(1200, 1200));
        assertTrue(TwinChildrenSwapLogic.due(1201, 1200));
    }

    @Test
    void nextSwapIsOneIntervalLater() {
        assertEquals(2400, TwinChildrenSwapLogic.scheduleNext(1200, 1200));
    }
}
