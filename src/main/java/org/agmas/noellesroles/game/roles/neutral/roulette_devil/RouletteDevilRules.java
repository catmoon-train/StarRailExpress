package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

/** 轮盘恶魔的纯规则，不依赖注册表，供服务端与确定性测试共用。 */
public final class RouletteDevilRules {
    public static final int SEED_TICKS = 30 * 20;
    public static final int COOLDOWN_SECONDS = 90;
    public static final int MANIFEST_TICKS = 20 * 20;
    /** 同一目标再次被同一恶魔拉入赌局的冷却；从赌局结束（含中途终止）开始计算。 */
    public static final int SAME_TARGET_COOLDOWN_SECONDS = 60;
    public static final int SAME_TARGET_COOLDOWN_TICKS = SAME_TARGET_COOLDOWN_SECONDS * 20;
    /** 赌局失败者存活返程时，随机获得的残疾患者负面效果时长。 */
    public static final int LOSER_DISABILITY_SECONDS = 3 * 60;
    public static final int LOSER_DISABILITY_TICKS = LOSER_DISABILITY_SECONDS * 20;
    /** 直视现身恶魔每秒扣除的理智上限比例。 */
    public static final float GAZE_SAN_PER_SECOND = .025F;
    public static final int ENTRY_TICKS = 20;

    public static int requiredWins(int startingPlayers) { return Math.max(0, startingPlayers) / 7 + 1; }
    public static int defeatAllowances(int startingPlayers) { return Math.max(1, Math.max(0, startingPlayers) / 8); }
    public static boolean allowDeath(boolean gameRunning, boolean settlingDefeat) { return !gameRunning || settlingDefeat; }
    public static boolean lowSan(double mood, double max) {
        return Double.isFinite(mood) && Double.isFinite(max) && max > 0 && mood / max < .30;
    }
    public static boolean mature(long plantedAt, long now) { return now >= plantedAt && now - plantedAt >= SEED_TICKS; }
    public static boolean eligible(boolean lowSan, boolean matureSeed) { return lowSan || matureSeed; }
    /** 视线扣减的结算间隔（秒）；每秒结算一次，直视才会掉理智。 */
    public static final int GAZE_INTERVAL_TICKS = 20;
    public static float gazeSanLoss(float max) {
        return Float.isFinite(max) && max > 0 ? max * GAZE_SAN_PER_SECOND : 0F;
    }
    public static boolean withinSeat(double dx, double dy, double dz) {
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && Math.abs(dx) <= .6 && Math.abs(dz) <= .6 && dy >= -1 && dy <= 3;
    }
    public static boolean spareDefeat(int startingPlayers, int previousLosses) {
        return previousLosses >= 0 && previousLosses < defeatAllowances(startingPlayers);
    }
    public static boolean cooling(long readyAt, long now) { return readyAt > now; }
    public static int cooldownSeconds(long readyAt, long now) {
        return readyAt <= now ? 0 : (int) Math.max(1, Math.ceil((readyAt - now) / 20.0));
    }
}
