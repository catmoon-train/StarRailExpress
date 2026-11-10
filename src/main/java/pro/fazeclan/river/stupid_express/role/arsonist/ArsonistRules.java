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
    public static final int RELOAD_TICKS = 130;
    public static final int INTRO_TICKS = 120;
    public static final int BODY_BURN_TICKS = 60;
    public static final int TITLE_START_TICK = 76;
    public static final int TITLE_STEP_TICKS = 8;
    public static final int FAILED_IGNITE_WINDOW_TICKS = 3 * 20;
    public static final int FULL_FUEL = 1000;
    public static final int FUEL_PER_PULSE = 15;
    public static final int HEAT_PER_TICK = 5;
    public static final int FUEL_PULSE_TICKS = 2;
    public static final double FLAME_RADIUS = 5.0D;
    /** 喷火锥半角，与短管霰弹枪一致：总锥角 70°。 */
    public static final double FLAME_HALF_ANGLE_DEGREES = 35.0D;
    private static final double FLAME_RANGE_SQ = FLAME_RADIUS * FLAME_RADIUS;
    private static final double FLAME_COS_HALF_ANGLE = Math.cos(Math.toRadians(FLAME_HALF_ANGLE_DEGREES));

    private ArsonistRules() {}

    /** -1仅代表原本就是无限时长；有限效果到期必须归零，不能产生永久效果。 */
    public static int remainingEffectTicks(int originalDuration, int elapsedTicks) {
        return originalDuration == -1 ? -1 : Math.max(0, originalDuration - Math.max(0, elapsedTicks));
    }

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

    public enum MomentResult { RUNNING, VICTORY, TIMEOUT }

    /** 杀光优先于超时，不能仅因撑满80秒宣胜。 */
    public static MomentResult momentResult(int ticksRemaining, int aliveOthers) {
        if (aliveOthers == 0) return MomentResult.VICTORY;
        return ticksRemaining <= 0 ? MomentResult.TIMEOUT : MomentResult.RUNNING;
    }

    /** 失败后主倒计时直接设为其他存活人数乘30秒，返回单位为tick。 */
    public static int failedMomentTimeTicks(int aliveOthers) {
        return Math.max(0, aliveOthers) * 30 * 20;
    }

    /**
     * 前方 5 格、半角 35° 的三维锥（以视线为轴，抬头/低头时随之倾斜），边界包含在内；
     * 实际命中另由服务端检查墙体遮挡。
     *
     * <p>用平方比较避免开方：dot² &gt;= cos²(半角) · |delta|² · |look|²，同时要求 dot &gt;= 0。
     */
    public static boolean inFlame(double x, double y, double z, double lookX, double lookY, double lookZ) {
        double distSq = x * x + y * y + z * z;
        if (distSq > FLAME_RANGE_SQ) return false;
        if (distSq < 1e-8D) return true;
        double lookSq = lookX * lookX + lookY * lookY + lookZ * lookZ;
        if (lookSq < 1e-8D) return false;
        double dot = x * lookX + y * lookY + z * lookZ;
        if (dot < 0) return false;
        return dot * dot >= FLAME_COS_HALF_ANGLE * FLAME_COS_HALF_ANGLE * distSq * lookSq;
    }
}
