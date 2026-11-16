package io.wifi.srepackcompat.cobblemon;

/** 按平均 tick 耗时收紧刷新预算；恶化时快降，持续改善后慢升，绝不因负载降至零。 */
public final class AdaptiveSpawnBudget {
    public static final float MAX_FRACTION = 0.4F;
    public static final float MIN_FRACTION = 0.1F;
    private static final int SAMPLE_INTERVAL = 20;
    private static final int RECOVERY_WINDOWS = 5;
    private static final float RECOVERY_STEP = 0.02F;

    private volatile float fraction = MAX_FRACTION;
    private int ticksUntilSample = SAMPLE_INTERVAL;
    private int recoveryWindows;

    public float fraction() { return fraction; }

    /** 参数为服务器自身统计的计算耗时，不包含维持目标 TPS 的正常休眠。 */
    public void observeAverageTickNanos(long averageNanos) {
        if (averageNanos < 0) return;
        if (--ticksUntilSample > 0) return;
        ticksUntilSample = SAMPLE_INTERVAL;
        float target = targetFraction(averageNanos);
        if (target <= fraction) {
            fraction = target;
            recoveryWindows = 0;
        } else if (++recoveryWindows >= RECOVERY_WINDOWS) {
            fraction = Math.min(target, fraction + RECOVERY_STEP);
            recoveryWindows = 0;
        }
    }

    static float targetFraction(long averageNanos) {
        double millis = averageNanos / 1_000_000.0;
        if (millis <= 40.0) return MAX_FRACTION;
        if (millis >= 100.0) return MIN_FRACTION;
        return (float) (MAX_FRACTION - (MAX_FRACTION - MIN_FRACTION) * (millis - 40.0) / 60.0);
    }

    /** 服务端启动、停止时重置，避免内置服务器换存档后继承上一个世界的压力。 */
    public void reset() {
        fraction = MAX_FRACTION;
        ticksUntilSample = SAMPLE_INTERVAL;
        recoveryWindows = 0;
    }
}
