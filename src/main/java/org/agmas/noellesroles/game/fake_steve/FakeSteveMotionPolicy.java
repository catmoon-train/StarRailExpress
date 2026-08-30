package org.agmas.noellesroles.game.fake_steve;

/** Shared limits for client-assisted, server-validated possessed movement. */
public final class FakeSteveMotionPolicy {
    public static final float MAX_TURN_DEGREES_PER_TICK = 18.0F;
    private static final float ROUTE_HEADING_DEAD_ZONE = 6.0F;
    private static final float MAX_ROUTE_HEADING_STEP = 24.0F;

    private FakeSteveMotionPolicy() {
    }

    public static float turnToward(float current, float target) {
        float delta = wrapDegrees(target - current);
        float step = Math.max(-MAX_TURN_DEGREES_PER_TICK,
                Math.min(MAX_TURN_DEGREES_PER_TICK, delta));
        return wrapDegrees(current + step);
    }

    /** Keeps adjacent A* nodes from making the body oscillate left and right. */
    public static float stableHeading(float previousTarget, float candidate) {
        float delta = wrapDegrees(candidate - previousTarget);
        if (Math.abs(delta) <= ROUTE_HEADING_DEAD_ZONE) {
            return wrapDegrees(previousTarget);
        }
        return wrapDegrees(previousTarget + Math.max(-MAX_ROUTE_HEADING_STEP,
                Math.min(MAX_ROUTE_HEADING_STEP, delta)));
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
