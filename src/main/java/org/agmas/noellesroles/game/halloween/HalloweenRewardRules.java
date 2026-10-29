package org.agmas.noellesroles.game.halloween;

/** 独立抽奖与骨剑旧存量迁移的纯规则。 */
public final class HalloweenRewardRules {
    public static final int BONE_DURABILITY = 3;
    public static final int BONE_COOLDOWN_TICKS = 30 * 20;
    private HalloweenRewardRules() {}

    public record TaskLoot(boolean pumpkin, boolean candy, boolean bag) {}

    public static TaskLoot taskLoot(double pumpkinRoll, double candyRoll, double bagRoll) {
        return new TaskLoot(hit(pumpkinRoll, .2), hit(candyRoll, .1), hit(bagRoll, .1));
    }

    private static boolean hit(double roll, double chance) {
        return Double.isFinite(roll) && roll >= 0 && roll < chance;
    }


    public static boolean keepsFreshSession(Long startedAt, long resetAt) {
        return startedAt != null && startedAt.longValue() == resetAt;
    }
    public static int migratedBoneDamage(int damage, int legacyUses) {
        return Math.min(BONE_DURABILITY, Math.max(0, Math.max(damage, legacyUses)));
    }
}
