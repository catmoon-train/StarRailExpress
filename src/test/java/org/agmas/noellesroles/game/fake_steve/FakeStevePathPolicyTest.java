package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FakeStevePathPolicyTest {
    @Test
    void jumpingRequiresARealStepAndRespectsCooldown() {
        assertTrue(FakeStevePathPolicy.shouldJump(true, true, 40L, 40L));
        assertFalse(FakeStevePathPolicy.shouldJump(false, true, 40L, 0L));
        assertFalse(FakeStevePathPolicy.shouldJump(true, false, 40L, 0L));
        assertFalse(FakeStevePathPolicy.shouldJump(true, true, 40L, 45L));
    }

    @Test
    void aNoJumpMapNeverReceivesAiJumpInput() {
        assertFalse(FakeStevePathPolicy.shouldJump(false, true, true, 40L, 40L));
        assertTrue(FakeStevePathPolicy.shouldJump(true, true, true, 40L, 40L));
    }

    @Test
    void noRouteProgressEventuallyBacksOffInsteadOfJumpingAtAWallForever() {
        assertFalse(FakeStevePathPolicy.hasStalled(9.0D, 8.7D, 100L, 120L));
        assertTrue(FakeStevePathPolicy.hasStalled(9.0D, 8.95D, 100L, 141L));
    }

    @Test
    void fakeSteveOpensClosedUnlockedSmallDoorsButNeverBypassesALock() {
        assertTrue(FakeStevePathPolicy.shouldAutoOpenSmallDoor(false, false));
        assertFalse(FakeStevePathPolicy.shouldAutoOpenSmallDoor(true, false));
        assertFalse(FakeStevePathPolicy.shouldAutoOpenSmallDoor(false, true));
    }

    @Test
    void swimmingAddsUpwardInputOnlyWhenTheRouteIsAboveTheBody() {
        assertTrue(FakeStevePathPolicy.shouldSwimUp(true, 10.0D, 10.4D));
        assertFalse(FakeStevePathPolicy.shouldSwimUp(true, 10.5D, 10.0D));
        assertFalse(FakeStevePathPolicy.shouldSwimUp(false, 10.0D, 12.0D));
    }

    @Test
    void pursuitAndPsychoBothSprintUnlessCrowdAvoidanceRequiresPrecision() {
        assertTrue(FakeStevePathPolicy.shouldSprintForPursuit(true, false, false));
        assertTrue(FakeStevePathPolicy.shouldSprintForPursuit(false, true, false));
        assertFalse(FakeStevePathPolicy.shouldSprintForPursuit(false, false, false));
        assertFalse(FakeStevePathPolicy.shouldSprintForPursuit(true, true, true));
    }

    @Test
    void carpetHeightIsWalkableButAFullBlockAtTheFeetIsNot() {
        assertTrue(FakeStevePathPolicy.isWalkThroughFootLayer(false, 0.0625D));
        assertFalse(FakeStevePathPolicy.isWalkThroughFootLayer(false, 1.0D));
        assertTrue(FakeStevePathPolicy.isWalkThroughFootLayer(true, 1.0D));
    }

    @Test
    void grassPathIsAWalkableNearFullBlockFootLayer() {
        // Minecraft's grass path collision ends at 15/16 of a block. It must
        // remain traversable so the body can step onto an adjacent grass block.
        assertTrue(FakeStevePathPolicy.isWalkThroughFootLayer(false, 0.9375D));
    }

    @Test
    void grassPathToGrassIsAMicroStepNotAForbiddenJump() {
        assertTrue(FakeStevePathPolicy.canStepUpWithoutJump(0.0625D));
        assertFalse(FakeStevePathPolicy.canStepUpWithoutJump(1.0D));
    }

    @Test
    void noJumpMapsNeverPlanAnAscendingNeighbour() {
        assertArrayEquals(new int[] { 0, -1 },
                FakeStevePathPolicy.verticalOffsets(false, false));
        assertArrayEquals(new int[] { 0, 1, -1 },
                FakeStevePathPolicy.verticalOffsets(true, false));
        assertArrayEquals(new int[] { 0, 1, -1 },
                FakeStevePathPolicy.verticalOffsets(false, true));
    }

    @Test
    void explicitTargetsPreferAClearStraightCorridor() {
        assertTrue(FakeStevePathPolicy.shouldPreferDirectRoute(true, true));
        assertFalse(FakeStevePathPolicy.shouldPreferDirectRoute(false, true));
        assertFalse(FakeStevePathPolicy.shouldPreferDirectRoute(true, false));
    }

    @Test
    void spectatorPlayersAreNeverTrackableAiTargets() {
        assertFalse(FakeStevePathPolicy.canTrackPlayer(true, true, false, true));
        assertFalse(FakeStevePathPolicy.canTrackPlayer(true, false, true, true));
        assertTrue(FakeStevePathPolicy.canTrackPlayer(true, false, false, true));
    }
}
