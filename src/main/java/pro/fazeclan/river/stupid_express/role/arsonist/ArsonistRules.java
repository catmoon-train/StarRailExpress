package pro.fazeclan.river.stupid_express.role.arsonist;

/** 纵火犯数值与纯计算规则，不依赖游戏注册表，便于无界面验证。 */
public final class ArsonistRules {
    public static final int TASK_COINS = 10;
    public static final int MARK_COINS = 35;
    public static final int OIL_CAN_PRICE = 280;
    public static final int FIRE_GUN_PRICE = 50;
    public static final int SHOT_COINS = 100;
    public static final int SHOT_COOLDOWN_TICKS = 30 * 20;
    public static final int MOMENT_TICKS = 80 * 20;
    public static final int RELOAD_TICKS = 10 * 20;
    public static final int FAILED_IGNITE_WINDOW_TICKS = 3 * 20;
    public static final int FULL_FUEL = 1000;
    public static final int FUEL_PER_PULSE = 15;
    public static final int HEAT_PER_TICK = 5;
    public static final int FUEL_PULSE_TICKS = 2;
    public static final double FLAME_RADIUS = 5.0D;

    private ArsonistRules() {}

    public static int requiredDoused(int aliveOthers) {
        return Math.max(1, (int) Math.ceil(Math.max(0, aliveOthers) * 0.8D));
    }

    public static int fuelAfterPulse(int fuel) {
        return Math.max(0, fuel - FUEL_PER_PULSE);
    }

    public static boolean shouldConsumeFuel(long now, long previousFuelTick) {
        return previousFuelTick == Long.MIN_VALUE || now - previousFuelTick >= FUEL_PULSE_TICKS;
    }

    public static int heatAfterTick(int heat) {
        return Math.min(100, Math.max(0, heat) + HEAT_PER_TICK);
    }

    /** 追随者纵火犯在时刻内每tick积攒1.5倍燃烧值：7与8交替，平均7.5点。 */
    public static int heatAfterTick(int heat, boolean boosted, boolean extraHalf) {
        int gain = boosted ? (extraHalf ? 8 : 7) : HEAT_PER_TICK;
        return Math.min(100, Math.max(0, heat) + gain);
    }

    /** 首次点火失败警告后，3秒内的第二次右键才会点火失败而死。 */
    public static boolean withinFailedIgniteWindow(long now, long warnedAt) {
        return warnedAt != Long.MIN_VALUE && now >= warnedAt && now - warnedAt <= FAILED_IGNITE_WINDOW_TICKS;
    }

    /** 按触发时全部存活人数（含本人）计算，每满3人给1层，最多4层。 */
    public static int momentShields(int alivePlayers) {
        return Math.min(4, Math.max(0, alivePlayers) / 3);
    }

    /** 前方半球，边界包含在内；实际命中另由服务端检查墙体遮挡。 */
    public static boolean inFlame(double x, double y, double z, double lookX, double lookY, double lookZ) {
        return x * x + y * y + z * z <= FLAME_RADIUS * FLAME_RADIUS
                && x * lookX + y * lookY + z * lookZ >= 0;
    }
}
