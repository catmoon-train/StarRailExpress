package pro.fazeclan.river.stupid_express.modifier.twin_children;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwinChildrenRideLogicTest {

    @Test
    void lowerStaysOnChairsAndOtherNonPlayerVehicles() {
        assertTrue(TwinChildrenRideLogic.keepLowerOnVehicle(false));
    }

    @Test
    void lowerDoesNotStayOnAnotherPlayer() {
        assertFalse(TwinChildrenRideLogic.keepLowerOnVehicle(true));
    }

    @Test
    void upperMountingASeatMovesTheStack() {
        assertTrue(TwinChildrenRideLogic.redirectMountToLower(true, false, false));
    }

    @Test
    void upperStayingOnTheLowerIsNotRedirected() {
        assertFalse(TwinChildrenRideLogic.redirectMountToLower(true, true, true));
    }

    @Test
    void lowerMountingASeatIsNotRedirected() {
        assertFalse(TwinChildrenRideLogic.redirectMountToLower(false, false, false));
    }
}
