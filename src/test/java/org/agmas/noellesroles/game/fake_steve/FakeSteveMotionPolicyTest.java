package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveMotionPolicyTest {

    @Test
    void turningUsesTheShortestArcAndNeverSnaps() {
        assertEquals(34.2F, FakeSteveMotionPolicy.turnToward(0.0F, 90.0F), 0.001F);
        assertEquals(-179.0F, FakeSteveMotionPolicy.turnToward(179.0F, -179.0F), 0.001F);
    }

    @Test
    void activeLeaseAcceptsSmallMovesInsideItsRouteCorridor() {
        FakeSteveMotionPolicy.Lease lease = new FakeSteveMotionPolicy.Lease(
                7L, 120L, 0.0D, 0.0D, 1.25D, 0.85D);

        assertTrue(FakeSteveMotionPolicy.accepts(lease, 100L,
                0.0D, 0.0D, 0.35D, 0.0D));
        assertFalse(FakeSteveMotionPolicy.accepts(lease, 100L,
                0.0D, 0.0D, 2.0D, 0.0D));
        assertFalse(FakeSteveMotionPolicy.accepts(lease, 121L,
                0.0D, 0.0D, 0.1D, 0.0D));
    }

    @Test
    void activeLeaseAcceptsProgressTowardADistantRoutePoint() {
        FakeSteveMotionPolicy.Lease lease = new FakeSteveMotionPolicy.Lease(
                8L, 120L, 5.5D, 0.5D, 1.75D, 0.85D);

        assertTrue(FakeSteveMotionPolicy.accepts(lease, 100L,
                2.5D, 0.0D, 2.7D, 0.0D));
    }

    @Test
    void correctionRequiresRepeatedRejectionAndMaterialDesync() {
        assertFalse(FakeSteveMotionPolicy.shouldCorrect(5, 9.0D));
        assertFalse(FakeSteveMotionPolicy.shouldCorrect(8, 1.0D));
        assertTrue(FakeSteveMotionPolicy.shouldCorrect(6, 4.1D));
    }

    @Test
    void smallRouteHeadingChangesUseADeadZoneInsteadOfWobbling() {
        assertEquals(30.0F, FakeSteveMotionPolicy.stableHeading(30.0F, 34.0F), 0.001F);
        assertEquals(50.0F, FakeSteveMotionPolicy.stableHeading(30.0F, 50.0F), 0.001F);
        assertEquals(66.0F, FakeSteveMotionPolicy.stableHeading(30.0F, 100.0F), 0.001F);
    }

    @Test
    void idleRunningIsOccasionalButDangerAlwaysTriggersEscape() {
        assertFalse(FakeSteveMotionPolicy.shouldSprint(false, 20, 0));
        assertTrue(FakeSteveMotionPolicy.shouldSprint(false, 140, 0));
        assertTrue(FakeSteveMotionPolicy.shouldSprint(true, 0, 99));
    }

    @Test
    void walkingGazeSometimesLooksAboveTheHorizon() {
        assertTrue(FakeSteveMotionPolicy.walkingPitch(0L, 7) <= 0.0F);
        assertTrue(FakeSteveMotionPolicy.walkingPitch(120L, 7) < 0.0F);
    }

    @Test
    void lookAheadIgnoresTheImmediateManhattanZigzag() {
        FakeSteveMotionPolicy.Point goal = new FakeSteveMotionPolicy.Point(5.5D, 4.5D);
        FakeSteveMotionPolicy.Point look = FakeSteveMotionPolicy.lookAheadPoint(List.of(
                new FakeSteveMotionPolicy.Point(1.5D, 0.5D),
                new FakeSteveMotionPolicy.Point(1.5D, 1.5D),
                new FakeSteveMotionPolicy.Point(2.5D, 1.5D),
                new FakeSteveMotionPolicy.Point(2.5D, 2.5D),
                new FakeSteveMotionPolicy.Point(3.5D, 2.5D)), goal);

        assertEquals(3.5D, look.x(), 0.001D);
        assertEquals(2.5D, look.z(), 0.001D);
        float beforeStep = FakeSteveMotionPolicy.yawTo(0.5D, 0.5D, look.x(), look.z());
        float afterStep = FakeSteveMotionPolicy.yawTo(1.5D, 0.5D, look.x(), look.z());
        assertEquals(beforeStep, FakeSteveMotionPolicy.walkingHeading(beforeStep, afterStep), 0.001F);
    }

    @Test
    void walkingHeadingIgnoresSmallCorridorWobble() {
        assertEquals(0.0F, FakeSteveMotionPolicy.walkingHeading(0.0F, 20.0F), 0.001F);
        assertEquals(12.0F, FakeSteveMotionPolicy.walkingHeading(0.0F, 90.0F), 0.001F);
    }

    @Test
    void localMoveKeepsForwardWhenLookingAlongTheWorldStep() {
        FakeSteveMotionPolicy.LocalMove aligned = FakeSteveMotionPolicy.toLocal(0.0F, 0.0D, 1.0D);
        assertEquals(1.0F, aligned.forward(), 0.001F);
        assertEquals(0.0F, aligned.strafe(), 0.001F);

        FakeSteveMotionPolicy.LocalMove sidestep = FakeSteveMotionPolicy.toLocal(0.0F, 1.0D, 0.0D);
        assertEquals(0.0F, sidestep.forward(), 0.001F);
        assertEquals(1.0F, sidestep.strafe(), 0.001F);
    }
}
