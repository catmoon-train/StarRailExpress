package org.agmas.noellesroles.game.fake_steve;

/** Pure guards for navigation recovery and movement actions. */
final class FakeStevePathPolicy {
    private FakeStevePathPolicy() {
    }

    static boolean shouldJump(boolean onGround, boolean ascends, long now, long nextJumpTick) {
        return onGround && ascends && now >= nextJumpTick;
    }

    static boolean hasStalled(double previousDistanceSqr, double currentDistanceSqr,
                              long lastProgressTick, long now) {
        return currentDistanceSqr >= previousDistanceSqr - 0.15D
                && now - lastProgressTick >= 40L;
    }

    static boolean shouldAutoOpenSmallDoor(boolean open, boolean hardLocked) {
        return !open && !hardLocked;
    }

    static boolean shouldSwimUp(boolean inWater, double bodyY, double targetY) {
        return inWater && targetY > bodyY + 0.2D;
    }
}
