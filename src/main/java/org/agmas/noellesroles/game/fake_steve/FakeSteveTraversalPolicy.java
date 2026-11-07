package org.agmas.noellesroles.game.fake_steve;

/** 导航与输入共用的纯规则，避免寻路认可了电梯、移动却反向游泳。 */
final class FakeSteveTraversalPolicy {
    static final int WATER = 0;
    static final int LIFT_UP = 1;
    static final int LIFT_DOWN = -1;

    private FakeSteveTraversalPolicy() {
    }

    static boolean allowsVertical(int lift, int deltaY) {
        return lift == WATER || Integer.signum(deltaY) == lift;
    }

    static boolean reached(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= 0.48D * 0.48D && Math.abs(dy) <= 0.6D;
    }

    static boolean repath(boolean empty, boolean invalid, boolean movedGoal,
            long now, long nextSearch) {
        return now >= nextSearch && (empty || invalid || movedGoal);
    }

    static boolean swimUp(int lift, double bodyY, double targetY) {
        return lift == WATER && targetY > bodyY + 0.15D;
    }

    static boolean swimDown(int lift, double bodyY, double targetY) {
        return lift == WATER && targetY < bodyY - 0.35D;
    }
}
