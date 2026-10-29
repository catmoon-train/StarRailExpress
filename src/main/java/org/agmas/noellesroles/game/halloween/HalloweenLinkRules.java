package org.agmas.noellesroles.game.halloween;

/** 与游戏注册表无关的链接计算，便于无客户端测试。 */
public final class HalloweenLinkRules {
    private HalloweenLinkRules() {}
    public static int sharedArmor(int own, int partner) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, own) + Math.max(0, partner));
    }
    public static int consumeOwn(int own, int requested) {
        return Math.min(Math.max(0, own), Math.max(0, requested));
    }
    public static boolean within(double distanceSquared, double limit) {
        return Double.isFinite(distanceSquared) && Double.isFinite(limit) && limit > 0
                && distanceSquared <= limit * limit;
    }
}
