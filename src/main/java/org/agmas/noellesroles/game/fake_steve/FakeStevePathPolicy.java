package org.agmas.noellesroles.game.fake_steve;

/** Pure guards for navigation recovery and movement actions. */
final class FakeStevePathPolicy {
    private static final int[] LEVEL_OR_DESCEND = { 0, -1 };
    private static final int[] LEVEL_ASCEND_OR_DESCEND = { 0, 1, -1 };

    private FakeStevePathPolicy() {
    }

    static boolean shouldJump(boolean onGround, boolean ascends, long now, long nextJumpTick) {
        return onGround && ascends && now >= nextJumpTick;
    }

    static boolean shouldJump(boolean jumpsAllowed, boolean onGround, boolean ascends,
                              long now, long nextJumpTick) {
        return jumpsAllowed && shouldJump(onGround, ascends, now, nextJumpTick);
    }

    static boolean hasStalled(double previousDistanceSqr, double currentDistanceSqr,
                              long lastProgressTick, long now) {
        return currentDistanceSqr >= previousDistanceSqr - 0.15D
                && now - lastProgressTick >= 40L;
    }

    /** The body wants to move but its position barely changes between AI samples. */
    static boolean isStuck(double movedDistanceSqr, long sampleGap) {
        return sampleGap >= 4L && movedDistanceSqr < 0.0025D;
    }

    /** Route nodes beside an open drop cost more, so the body hugs the deck. */
    static int edgePenalty(boolean dropBeside) {
        return dropBeside ? 4 : 0;
    }

    static boolean needsRecalculation(int stuckTicks) {
        return stuckTicks >= 3;
    }

    static boolean shouldAutoOpenSmallDoor(boolean open, boolean hardLocked) {
        return !open && !hardLocked;
    }

    static boolean shouldSwimUp(boolean inWater, double bodyY, double targetY) {
        return inWater && targetY > bodyY + 0.2D;
    }

    static boolean shouldSprintForPursuit(boolean pursuingHuman, boolean psychoActive,
                                          boolean crowdBlocked) {
        return !crowdBlocked && (pursuingHuman || psychoActive);
    }

    static boolean isWalkThroughFootLayer(boolean collisionEmpty, double collisionMaxY) {
        // Grass paths are 15/16 high. They are not walls: vanilla can step from
        // their top onto a neighbouring full block without jumping.
        return collisionEmpty || collisionMaxY < 1.0D;
    }

    /** A vanilla player can step up 0.6 blocks without a jump input. */
    static boolean canStepUpWithoutJump(double verticalRise) {
        return verticalRise <= 0.6D;
    }

    static int[] verticalOffsets(boolean jumpsAllowed, boolean swimming) {
        return jumpsAllowed || swimming ? LEVEL_ASCEND_OR_DESCEND : LEVEL_OR_DESCEND;
    }

    static boolean shouldPreferDirectRoute(boolean explicitTarget, boolean corridorClear) {
        return explicitTarget && corridorClear;
    }

    static boolean canTrackPlayer(boolean alive, boolean spectator, boolean creative,
                                  boolean survivalParticipant) {
        return alive && !spectator && !creative && survivalParticipant;
    }
}
