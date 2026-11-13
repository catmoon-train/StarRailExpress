package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

/** 轮盘恶魔的纯规则，不依赖注册表，供服务端与确定性测试共用。 */
public final class RouletteDevilRules {
    public static final int SEED_TICKS = 30 * 20;
    public static final int COOLDOWN_SECONDS = 90;
    public static final int MANIFEST_TICKS = 12 * 20;
    public static final int ENTRY_TICKS = 20;
    private RouletteDevilRules() {}

    public static int requiredWins(int startingPlayers) { return Math.max(0, startingPlayers) / 5 + 1; }
    public static int defeatAllowances(int startingPlayers) { return Math.max(1, Math.max(0, startingPlayers) / 8); }
    public static boolean lowSan(double mood, double max) {
        return Double.isFinite(mood) && Double.isFinite(max) && max > 0 && mood / max < .30;
    }
    public static boolean mature(long plantedAt, long now) { return now >= plantedAt && now - plantedAt >= SEED_TICKS; }
    public static boolean eligible(boolean lowSan, boolean matureSeed) { return lowSan || matureSeed; }
    public static float manifestationDrain(float original, boolean seeing) { return seeing ? original * 6 : original; }
    public static boolean withinSeat(double dx, double dy, double dz) {
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && Math.abs(dx) <= .6 && Math.abs(dz) <= .6 && dy >= -1 && dy <= 3;
    }
    public static boolean spareDefeat(int startingPlayers, int previousLosses) {
        return previousLosses >= 0 && previousLosses < defeatAllowances(startingPlayers);
    }
}
