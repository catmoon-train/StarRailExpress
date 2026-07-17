package net.exmo.sre.sixtyseconds.client.screen;

/**
 * 客户端战斗状态追踪。
 * <p>
 * SeaChartFullScreen 用此判断玩家是否脱战（脱战才能返回住所）。
 * 服务端通过专用的 S2C 包同步战斗状态；本缓存保留最近 5 秒。
 * </p>
 */
public final class SixtySecondsCombatClient {

    /** 最后一次接收到的「进入战斗」tick（客户端 tick）。 */
    private static long lastCombatClientTick = -1000;
    /** 战斗状态保留时间（tick）。 */
    public static final long COMBAT_TIMEOUT_TICKS = 20 * 5;

    private SixtySecondsCombatClient() {
    }

    public static boolean isInCombat() {
        // 用系统毫秒近似判断（客户端 receiveTick ≈ 实际游戏 tick）
        long now = System.currentTimeMillis() / 50;
        return (now - lastCombatClientTick) < COMBAT_TIMEOUT_TICKS;
    }

    public static void markCombat() {
        lastCombatClientTick = System.currentTimeMillis() / 50;
    }

    public static void clearCombat() {
        lastCombatClientTick = -1000;
    }
}
