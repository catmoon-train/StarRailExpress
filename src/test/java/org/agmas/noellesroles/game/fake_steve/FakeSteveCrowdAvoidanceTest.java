package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveCrowdAvoidanceTest {
    @Test
    void playerDirectlyAheadCausesLateralAvoidanceInsteadOfWalkingIntoThem() {
        FakeSteveCrowdAvoidance.Decision decision = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0.0D, 1.0D)), 0);

        assertTrue(decision.crowded());
        assertTrue(decision.forwardScale() <= 0.4F);
        assertTrue(Math.abs(decision.strafe()) >= 0.5F);
    }

    @Test
    void playerOutsideTheMovementCorridorDoesNotCauseWobble() {
        FakeSteveCrowdAvoidance.Decision decision = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(2.0D, 0.5D)), 0);

        assertEquals(1.0F, decision.forwardScale(), 0.001F);
        assertEquals(0.0F, decision.strafe(), 0.001F);
        assertFalse(decision.crowded());
    }

    @Test
    void prolongedPlayerBlockageRequestsAPathReplan() {
        FakeSteveCrowdAvoidance.Decision decision = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0.0D, 1.0D)), 8);

        assertTrue(decision.shouldRepath());
    }

    @Test
    void tinyObstacleJitterDoesNotFlipTheChosenAvoidanceSide() {
        FakeSteveCrowdAvoidance.Decision rightJitter = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0.02D, 1.0D)), 5);
        FakeSteveCrowdAvoidance.Decision leftJitter = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(-0.02D, 1.0D)), 5);

        assertEquals(Math.signum(rightJitter.strafe()), Math.signum(leftJitter.strafe()));
    }
}
