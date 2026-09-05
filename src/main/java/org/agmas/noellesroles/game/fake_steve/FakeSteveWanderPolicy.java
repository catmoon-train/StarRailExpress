package org.agmas.noellesroles.game.fake_steve;

/** Destination rules for idle wandering so the body does not pace one tile. */
public final class FakeSteveWanderPolicy {
    public static final double MIN_TASK_DISTANCE_SQR = 64.0D;
    public static final double SOCIAL_MIN_SQR = 36.0D;
    public static final double SOCIAL_MAX_SQR = 100.0D;
    public static final double AVOID_LAST_DISTANCE = 6.0D;
    public static final double AVOID_PLATE_DISTANCE = 4.0D;

    private FakeSteveWanderPolicy() {
    }

    public static boolean isUsableTaskPoint(double distanceSqr, boolean nearLastWander,
            boolean recentPlate) {
        return !recentPlate && !nearLastWander && distanceSqr >= MIN_TASK_DISTANCE_SQR;
    }

    public static boolean isSocialStand(double distanceToPlayerSqr) {
        return distanceToPlayerSqr >= SOCIAL_MIN_SQR && distanceToPlayerSqr <= SOCIAL_MAX_SQR;
    }

    public static boolean shouldReselectNow(boolean noGoal, int pathFailures, boolean decisionDue) {
        return noGoal || pathFailures >= 2 || decisionDue;
    }
}
