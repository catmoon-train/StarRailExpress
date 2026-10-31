package org.agmas.noellesroles.game.halloween.village;

/** 不依赖游戏类的时序和概率规则，便于独立验证。 */
public final class VillageRules {
    private VillageRules() {}

    public static boolean isNight(long elapsed, int start, int duration, int interval) {
        long first = Math.max(0, start);
        long period = Math.max(1, interval);
        return elapsed >= first && (elapsed - first) % period < Math.min(period, Math.max(0, duration));
    }

    /** 0=南瓜，1=亡灵，2=糖果袋；随机数必须在 [0,100) 内。 */
    public static int knockReward(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("roll");
        return roll < 60 ? 0 : roll < 90 ? 1 : 2;
    }

    public static boolean cooldownReady(long now, long next) { return now >= next; }

    /** 地图设置里的残月坐标；(0,0,0) 视为未设置，此时不生成固定残月。 */
    public static boolean isSpawnConfigured(int x, int y, int z) { return x != 0 || y != 0 || z != 0; }
}
