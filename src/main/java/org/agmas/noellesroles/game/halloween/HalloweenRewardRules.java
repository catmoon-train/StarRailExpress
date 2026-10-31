package org.agmas.noellesroles.game.halloween;

/** 独立抽奖与骨剑旧存量迁移的纯规则。 */
public final class HalloweenRewardRules {
    public static final int BONE_DURABILITY = 3;
    public static final int BONE_COOLDOWN_TICKS = 30 * 20;
    public static final int SAW_COOLDOWN_TICKS = 40 * 20;
    public static final int GUN_COOLDOWN_TICKS = 30 * 20;
    public static final int GUN_USES = 3;

    public static int gunCooldown(int configured) { return Math.max(GUN_COOLDOWN_TICKS, configured); }
    private HalloweenRewardRules() {}

    public record TaskLoot(int pumpkinCount, boolean candy, boolean bag) {}

    public static TaskLoot taskLoot(double pumpkinRoll, double candyRoll, double bagRoll) {
        return new TaskLoot(hit(pumpkinRoll, .5) ? 1 : 2, hit(candyRoll, .1), hit(bagRoll, .1));
    }

    /** 糖果额外效果：护盾 10%，跳过任务、无限体力、夜视各 30%。 */
    public static int candyEffect(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("roll");
        return roll < 10 ? 0 : roll < 40 ? 1 : roll < 70 ? 2 : 3;
    }

    /** 新南瓜奖励增加 30%，小数以十分位随机取整；使用 long 避免数量溢出。 */
    public static int boostedPumpkins(int count, int roll) {
        if (roll < 0 || roll >= 10) throw new IllegalArgumentException("roll");
        if (count <= 0) return 0;
        long scaled = (long) count * 13;
        long result = scaled / 10 + (roll < scaled % 10 ? 1 : 0);
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    /** 平民新奖励减半，奇数部分以 50% 概率进位；其余阵营保持原数。 */
    public static int pumpkinReward(int count, boolean civilian, boolean roundUp) {
        if (count <= 0) return 0;
        return civilian ? count / 2 + ((count & 1) != 0 && roundUp ? 1 : 0) : count;
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
