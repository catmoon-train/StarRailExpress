package org.agmas.noellesroles.game.dio;

/** 压路机的共享轨迹与扫掠计算，不依赖游戏注册表，供两端及单元测试共用。 */
public final class RoadRollerMotion {
    public static final double RADIUS = 5.0;
    public static final double HEIGHT = 3.0;
    public static final int BUY_COOLDOWN = 90 * 20;
    public static final int USE_COOLDOWN = 150 * 20;
    public static final int RESTORE_DELAY = 5 * 20;

    private RoadRollerMotion() {
    }

    public static double raisedFeetY(double originY, double mapCeiling, double worldCeiling, double playerHeight) {
        return Math.min(originY + 30.0, Math.min(mapCeiling, worldCeiling) - playerHeight - 0.05);
    }

    public static double rollerY(double startY, double endY, double age, int hold, int duration) {
        double t = Math.max(0.0, Math.min(1.0, (age - hold) / Math.max(1, duration - hold)));
        return startY + (endY - startY) * t * t;
    }

    /** 动作生命周期只看下砸计时，不等待时停效果结束。 */
    public static boolean isFinished(int age, int duration) {
        return age >= duration;
    }

    /** 球体沿竖直路径扫掠与目标包围盒求交，避免高速下砸跨过目标。 */
    public static boolean intersects(double x, double z, double fromY, double toY,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.max(minX - x, x - maxX), 0.0);
        double dz = Math.max(Math.max(minZ - z, z - maxZ), 0.0);
        double low = Math.min(fromY, toY);
        double high = Math.max(fromY, toY);
        double dy = Math.max(Math.max(minY - high, low - maxY), 0.0);
        return dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS;
    }

    /** 结束五秒后，用约三秒时间分批恢复，始终限制单 tick 工作量。 */
    public static int restoreBudget(int blockCount) {
        return Math.max(1, (blockCount + 59) / 60);
    }
}
