package io.wifi.starrailexpress.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainInteriorDetectionTest {
    @Test
    void solidRoofCountsEvenWhenSkyLightPassesThrough() {
        assertTrue(TrainInteriorDetection.isCovered((x, y, z) -> x == 0 && z == 0 && y == 2));
    }

    @Test
    void tallDiningCarRoofIsInside() {
        assertTrue(TrainInteriorDetection.isCovered((x, y, z) -> x == 0 && z == 0 && y == 12));
    }

    @Test
    void connectorGapIsBridgedAlongEitherTrainAxis() {
        assertTrue(TrainInteriorDetection.isCovered((x, y, z) -> Math.abs(x) == 1 && z == 0 && y == 3));
        assertTrue(TrainInteriorDetection.isCovered((x, y, z) -> Math.abs(z) == 2 && x == 0 && y == 4));
    }

    @Test
    void unevenRoofsOnBothSidesStillCoverConnector() {
        assertTrue(TrainInteriorDetection.isCovered((x, y, z) -> z == 0
                && ((x == -1 && y == 2) || (x == 2 && y == 4))));
    }

    @Test
    void openAirAndSingleSidedEavesStayOutside() {
        assertFalse(TrainInteriorDetection.isCovered((x, y, z) -> false));
        assertFalse(TrainInteriorDetection.isCovered((x, y, z) -> x == 1 && y == 3));
    }

    @Test
    void floorBelowPlayerOnTrainRoofDoesNotCountAsCeiling() {
        assertFalse(TrainInteriorDetection.isCovered((x, y, z) -> y <= 0));
    }

    @Test
    void distantRoofsDoNotBridgeWideOutdoorGaps() {
        assertFalse(TrainInteriorDetection.isCovered((x, y, z) -> Math.abs(x) == 3 && y == 3));
        assertFalse(TrainInteriorDetection.isCovered((x, y, z) -> y == 13));
    }

    @Test
    void scanStaysWithinBoundedLocalArea() {
        TrainInteriorDetection.isCovered((x, y, z) -> {
            assertTrue(Math.abs(x) <= 2 && Math.abs(z) <= 2);
            assertTrue(y > 0 && y <= 12);
            assertTrue(x == 0 || z == 0);
            return false;
        });
    }
}
