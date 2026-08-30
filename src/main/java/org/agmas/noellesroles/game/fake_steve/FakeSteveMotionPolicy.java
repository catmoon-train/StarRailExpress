package org.agmas.noellesroles.game.fake_steve;

/** Shared limits for client-assisted, server-validated possessed movement. */
public final class FakeSteveMotionPolicy {
    public static final float MAX_TURN_DEGREES_PER_TICK = 42.0F;
    private static final float TURN_RESPONSE = 0.38F;
    private static final float MIN_TURN_DEGREES_PER_TICK = 2.5F;
    private static final float ROUTE_HEADING_DEAD_ZONE = 6.0F;
    private static final float MAX_ROUTE_HEADING_STEP = 36.0F;
    private static final float STRAIGHT_HEADING_DEAD_ZONE = 14.0F;
    private static final float MAX_STRAIGHT_HEADING_STEP = 12.0F;

    private FakeSteveMotionPolicy() {
    }

    public static float turnToward(float current, float target) {
        float delta = wrapDegrees(target - current);
        if (Math.abs(delta) <= MIN_TURN_DEGREES_PER_TICK) {
            return wrapDegrees(target);
        }
        float magnitude = Math.min(MAX_TURN_DEGREES_PER_TICK,
                Math.max(MIN_TURN_DEGREES_PER_TICK, Math.abs(delta) * TURN_RESPONSE));
        float step = Math.copySign(magnitude, delta);
        return wrapDegrees(current + step);
    }

    /** Keeps adjacent A* nodes from making the body oscillate left and right. */
    public static float stableHeading(float previousTarget, float candidate) {
        return stableHeading(previousTarget, candidate, false);
    }

    /**
     * Straight routes get a much wider dead zone: a body walking down a corridor
     * should keep its head still instead of sweeping it across every node.
     */
    public static float stableHeading(float previousTarget, float candidate, boolean straight) {
        float delta = wrapDegrees(candidate - previousTarget);
        float deadZone = straight ? STRAIGHT_HEADING_DEAD_ZONE : ROUTE_HEADING_DEAD_ZONE;
        float maxStep = straight ? MAX_STRAIGHT_HEADING_STEP : MAX_ROUTE_HEADING_STEP;
        if (Math.abs(delta) <= deadZone) {
            return wrapDegrees(previousTarget);
        }
        return wrapDegrees(previousTarget + Math.max(-maxStep, Math.min(maxStep, delta)));
    }

    public static boolean isStraightAhead(float previousTarget, float candidate) {
        return Math.abs(wrapDegrees(candidate - previousTarget)) <= STRAIGHT_HEADING_DEAD_ZONE;
    }

    /** Human-looking sprint policy: flee immediately, otherwise only after lingering. */
    public static boolean shouldSprint(boolean danger, int idleTicks, int chanceRoll) {
        return danger || (idleTicks >= 120 && Math.floorMod(chanceRoll, 5) == 0);
    }

    /** A slow, deterministic gaze cycle that includes occasional upward glances. */
    public static float walkingPitch(long gameTime, int personalitySeed) {
        int phase = Math.floorMod(Math.floorDiv(gameTime + Math.floorMod(personalitySeed, 100), 100L), 5);
        return switch (phase) {
            case 0 -> -8.0F;
            case 1 -> -3.0F;
            case 2 -> 4.0F;
            case 3 -> -12.0F;
            default -> 1.0F;
        };
    }

    /**
     * Travelling a straight route uses one fixed, per-body gaze angle. The
     * periodic nodding cycle is only used while idling or turning.
     */
    public static float walkingPitch(long gameTime, int personalitySeed, boolean steady) {
        if (!steady) {
            return walkingPitch(gameTime, personalitySeed);
        }
        return switch (Math.floorMod(personalitySeed, 4)) {
            case 0 -> -3.0F;
            case 1 -> -2.0F;
            case 2 -> -4.0F;
            default -> -1.0F;
        };
    }

    /** Slow left/right scanning while standing around, so idle bodies look alive. */
    public static float idleScanYaw(long gameTime, int personalitySeed, float anchorYaw) {
        int period = 140 + Math.floorMod(personalitySeed, 140);
        double phase = (double) Math.floorMod(gameTime + Math.floorMod(personalitySeed, 97), period)
                / (double) period;
        return wrapDegrees(anchorYaw + (float) (Math.sin(phase * 2.0D * Math.PI) * 26.0D));
    }

    public static boolean accepts(Lease lease, long now,
            double previousX, double previousZ, double nextX, double nextZ) {
        if (lease == null || now > lease.expiresAtTick()) {
            return false;
        }
        double stepX = nextX - previousX;
        double stepZ = nextZ - previousZ;
        if (stepX * stepX + stepZ * stepZ > lease.maxStep() * lease.maxStep()) {
            return false;
        }
        double previousRouteX = previousX - lease.routeX();
        double previousRouteZ = previousZ - lease.routeZ();
        double nextRouteX = nextX - lease.routeX();
        double nextRouteZ = nextZ - lease.routeZ();
        double previousDistance = Math.sqrt(previousRouteX * previousRouteX
                + previousRouteZ * previousRouteZ);
        double nextDistance = Math.sqrt(nextRouteX * nextRouteX + nextRouteZ * nextRouteZ);
        // A lease may begin before the body has entered the final node's corridor.
        // Permit bounded progress toward it instead of rejecting valid vanilla movement.
        if (nextDistance <= lease.corridorRadius()) {
            return true;
        }
        return nextDistance <= previousDistance + 0.15D;
    }

    public static boolean shouldCorrect(int consecutiveRejectedPackets,
            double desyncDistanceSqr) {
        return consecutiveRejectedPackets >= 6 && desyncDistanceSqr > 4.0D;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public record Lease(long sequence, long expiresAtTick,
            double routeX, double routeZ, double corridorRadius,
            double maxStep) {
    }
}
