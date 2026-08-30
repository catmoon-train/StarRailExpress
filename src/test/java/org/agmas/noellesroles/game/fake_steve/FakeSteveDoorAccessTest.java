package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveDoorAccessTest {
    @Test
    void offsetDoorAnchorsAreDetectedAlongTheApproachCorridor() {
        assertTrue(FakeSteveDoorAccess.isInsideApproachCorridor(
                0.0D, 0.0D, 0.0D, 2.0D, 0.75D, 1.0D));
        assertFalse(FakeSteveDoorAccess.isInsideApproachCorridor(
                0.0D, 0.0D, 0.0D, 2.0D, 2.0D, 1.0D));
    }
}
