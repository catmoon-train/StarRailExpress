package org.agmas.noellesroles.game.roles.neutral.silver_wing;

/**
 * 银翼数值与判定。保持无 Minecraft 依赖，便于单测钉死职业文档。
 */
public final class SilverWingRules {
    public static final int EMP_SKILL_COOLDOWN_SECONDS = 50;
    public static final int EMP_USE_COOLDOWN_SECONDS = 15;
    public static final int EMP_MAX_OWNED = 1;
    public static final int EMP_ITEM_BAN_SECONDS = 4;
    public static final int EMP_SLOWNESS_SECONDS = 4;
    /** 缓慢 III，对应药水 amplifier = 2 */
    public static final int EMP_SLOWNESS_AMPLIFIER = 2;

    public static final int BIRD_LIFETIME_SECONDS = 25;
    public static final int BIRD_SHOP_PRICE = 125;
    public static final double BIRD_EXPLOSION_RADIUS = 2.5D;
    public static final double BIRD_AURA_RADIUS = 4.0D;
    public static final int BIRD_BLINDNESS_SECONDS = 3;
    public static final int BIRD_ITEM_BAN_SECONDS = 3;
    public static final int BIRD_SKILL_BAN_SECONDS = 8;
    public static final float BIRD_MOOD_DRAIN = 0.30F;
    public static final int BIRD_GOLD_PENALTY = 25;
    public static final float BIRD_SHIELD_BREAK_CHANCE = 0.75F;
    public static final double BIRD_FLY_SPEED = 0.45D;
    public static final double BIRD_DASH_SPEED = 1.15D;
    public static final int BIRD_DASH_EXPLODE_SECONDS = 3;
    public static final float BIRD_HURT_DAMAGE = 1.0F;
    public static final double BIRD_KNOCKBACK = 1.15D;
    public static final double BIRD_KNOCKBACK_Y = 0.38D;

    /** 平民任务默认奖励，银翼做任务拿这份金币。 */
    public static final int TASK_GOLD = 50;

    private SilverWingRules() {
    }

    public static int ticks(int seconds) {
        return seconds * 20;
    }

    public static boolean alreadyHasEmpBomb(int ownedCount) {
        return ownedCount >= EMP_MAX_OWNED;
    }

    /**
     * 使用电磁脉冲炸弹后：技能当前没有冷却时，才进入 15 秒冷却。
     */
    public static boolean shouldApplyEmpUseCooldown(boolean skillOnCooldown) {
        return !skillOnCooldown;
    }

    public static boolean shouldBreakShield(float randomRoll) {
        return randomRoll < BIRD_SHIELD_BREAK_CHANCE;
    }

    public static float drainMood(float currentMood) {
        return Math.max(0.0F, currentMood - BIRD_MOOD_DRAIN);
    }

    public static int deductGold(int balance) {
        return Math.max(0, balance - BIRD_GOLD_PENALTY);
    }

    public static boolean isWithinExplosion(double distanceSqr) {
        return distanceSqr <= BIRD_EXPLOSION_RADIUS * BIRD_EXPLOSION_RADIUS;
    }

    public static boolean isWithinAura(double distanceSqr) {
        return distanceSqr <= BIRD_AURA_RADIUS * BIRD_AURA_RADIUS;
    }

    /**
     * 爆炸水平击退：从爆心指向目标。目标几乎叠在爆心时，改向 X 轴推开。
     */
    public static double[] horizontalKnockback(double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-4D) {
            return new double[] {BIRD_KNOCKBACK, 0.0D};
        }
        return new double[] {dx / length * BIRD_KNOCKBACK, dz / length * BIRD_KNOCKBACK};
    }
}
