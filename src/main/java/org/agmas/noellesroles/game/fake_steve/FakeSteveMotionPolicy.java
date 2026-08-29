package org.agmas.noellesroles.game.fake_steve;

/** Shared limits for client-assisted, server-validated possessed movement. */
public final class FakeSteveMotionPolicy {
    public static final float MAX_TURN_DEGREES_PER_TICK = 12.0F;

    private FakeSteveMotionPolicy() {
    }

    public static float turnToward(float current, float target) {
        float delta = wrapDegrees(target - current);
        float step = Math.max(-MAX_TURN_DEGREES_PER_TICK,
                Math.min(MAX_TURN_DEGREES_PER_TICK, delta));
        return wrapDegrees(current + step);
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
        double corridorX = nextX - lease.routeX();
        double corridorZ = nextZ - lease.routeZ();
        return corridorX * corridorX + corridorZ * corridorZ
                <= lease.corridorRadius() * lease.corridorRadius();
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
