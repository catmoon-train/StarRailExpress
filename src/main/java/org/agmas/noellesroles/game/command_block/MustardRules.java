package org.agmas.noellesroles.game.command_block;

/** 芥末与傀儡的数值规则，不依赖游戏注册表。 */
public final class MustardRules {
    public static final int CORE_PRICE = 100;
    public static final int CORE_PRICE_STEP = 25;
    public static final int HAND_DAMAGE = 5;
    public static final int GUARD_TICKS = 30 * 20;
    public static final int SKILL_COOLDOWN_SECONDS = 120;

    private MustardRules() {
    }

    public static int puppetDamage(int upgrades) {
        return (int) Math.min(Integer.MAX_VALUE, 5L + 5L * Math.max(0, upgrades));
    }

    public static boolean guardActive(long expiresAt, long now) {
        return expiresAt > now;
    }
}
