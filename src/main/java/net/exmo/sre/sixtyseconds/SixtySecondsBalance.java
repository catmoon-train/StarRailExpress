package net.exmo.sre.sixtyseconds;

/**
 * 末日60秒模式的数值平衡集中表——所有可调数值放这里，便于统一调参。
 */
public final class SixtySecondsBalance {
    private SixtySecondsBalance() {
    }

    // ── 每分钟状态消耗（户外基准；在家 ×{@link #HOME_DRAIN_MULT}）───────────
    public static final int HUNGER_DRAIN_PER_MIN = 3;   // 一天(8min)约 -24，≈半条
    public static final int THIRST_DRAIN_PER_MIN = 5;   // 一天约 -40，≈一条
    public static final int SANITY_DRAIN_PER_MIN = 1;   // san 缓慢下降
    public static final int POLLUTION_GAIN_PER_MIN = 1; // 污染缓慢累积
    public static final double HOME_DRAIN_MULT = 0.5;   // 在家各项下降/累积减半

    // ── 健康保护（避免多状态叠加导致掉血过快）─────────────────────────────
    /** 饥饿或口渴清空时，每秒最多扣的健康（<b>单一来源、封顶、不叠加</b>）。100 血≈100s 才死，留足反制窗口。 */
    public static final int HEALTH_LOSS_PER_SEC = 1;

    // ── 污染满的负面（不立即死亡）──────────────────────────────────────
    public static final int POLLUTION_SICK_ROLL_INTERVAL = 20 * 120; // 满污染每 2 分钟一次生病判定
    public static final double POLLUTION_SICK_CHANCE = 0.33;

    // ── 事件系统 ──────────────────────────────────────────────────────
    public static final int EVENT_CHECK_INTERVAL = 20 * 60 * 3; // 每 3 分钟尝试触发一次事件
    public static final double EVENT_CHANCE = 0.6;              // 触发概率
    public static final int POLLUTION_RAIN_DURATION = 20 * 60 * 2; // 污雨持续 2 分钟
    public static final int POLLUTION_RAIN_GAIN_PER_SEC = 2;    // 污雨中·户外·无伞：每秒额外污染

    // ── san 归零变怪物 ────────────────────────────────────────────────
    public static final int MONSTER_DELAY_TICKS = 20 * 30;    // san 归零后 30s 变怪
    public static final int SAN_LOSS_ON_DEATH = 15;           // 目睹死亡损失的 san
    public static final double DEATH_SAN_RANGE_SQR = 24 * 24; // 目睹死亡的范围（平方）

    // ── 日内子相位：早晨禁 PvP ────────────────────────────────────────
    public static final int MORNING_TICKS = 20 * 60 * 2; // 每日前 2 分钟为早晨，禁止攻击玩家

    // ── 睡眠（每个游戏日尾声）────────────────────────────────────────────
    public static final int NIGHT_WINDOW_TICKS = 20 * 60;      // 每日最后 1 分钟为夜晚
    public static final int SLEEP_HEAL_PER_SEC = 3;            // 在家床上睡觉每秒回血
    public static final int NIGHT_NO_SLEEP_LOSS_PER_SEC = 1;   // 夜晚不在床/在户外每秒扣血
    public static final double NIGHT_OUTDOOR_SICK_CHANCE = 0.2; // 户外过夜每 10s 生病判定
}
