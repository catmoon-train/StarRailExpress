package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

/** 布袋鬼与诡客的纯数值规则；不初始化物品或世界注册表。 */
public final class SackGhostRules {
    public static final int MAX_ART_ITEMS = 4;
    public static final int CAST_INTERVAL = 40;
    public static final int REALM_COOLDOWN = 120 * 20;
    public static final int REALM_ATTACK_INTERVAL = 20;
    public static final double REALM_ATTACK_RANGE = 3.3;
    public static final int RELIC_WARD_COOLDOWN = 45 * 20;
    public static final int RELIC_BREAK_COOLDOWN = 60 * 20;
    public static final int RELIC_BREAK_COST = 175;
    public static final double RELIC_RANGE = 20;
    public static final int WARD_DURATION = 6 * 20;
    public static final double EROSION_MULTIPLIER = 1.3;

    private SackGhostRules() {}

    public static int generationCooldownSeconds(int initialPlayers) {
        return Math.max(10, 60 - Math.max(0, initialPlayers));
    }

    public static int stageThreshold(int stage, int initialPlayers) {
        int n = Math.clamp(initialPlayers, 1, 128);
        return switch (stage) {
            case 2 -> Math.max(90, 8 * n);
            case 3 -> Math.max(220, 18 * n);
            case 4 -> Math.max(400, 32 * n);
            default -> 0;
        };
    }

    public static int stageForFear(double actualSanLoss, int initialPlayers) {
        if (!Double.isFinite(actualSanLoss) || actualSanLoss < 0) return 1;
        for (int stage = 4; stage >= 2; stage--) {
            if (actualSanLoss >= stageThreshold(stage, initialPlayers)) return stage;
        }
        return 1;
    }

    public static double actualSanLoss(double before, double after) {
        if (!Double.isFinite(before) || !Double.isFinite(after)) return 0;
        return Math.max(0, Math.max(0, before) - Math.max(0, after)) * 100;
    }

    public static int realmAttackSan(int stage) { return stage >= 4 ? 25 : 20; }
    public static int realmDuration(int stage) { return stage >= 4 ? 800 : 600; }

    public static boolean isCondemned(double sanFraction) {
        return Double.isFinite(sanFraction) && sanFraction <= 0;
    }

    public static boolean relicCanReveal(boolean eligibleCarrier, boolean activeRealm, double distanceSquared) {
        return eligibleCarrier && activeRealm && Double.isFinite(distanceSquared)
                && distanceSquared >= 0 && distanceSquared <= RELIC_RANGE * RELIC_RANGE;
    }

    public static boolean canReceiveArts(int owned, int incoming) {
        return owned >= 0 && incoming > 0 && incoming <= MAX_ART_ITEMS && owned <= MAX_ART_ITEMS - incoming;
    }

    public static int remainingEffectTicks(int duration, long elapsed) {
        return duration == -1 ? -1 : (int) Math.max(0, duration - Math.max(0, elapsed));
    }

    public static int effectRefreshDuration(int remaining, int currentDuration) {
        if (remaining <= 0 || currentDuration == -1 || currentDuration >= remaining) return 0;
        return (int) Math.min(Integer.MAX_VALUE, (long) remaining + 20);
    }

    public static int remaining(long now, long readyAt) {
        if (readyAt <= now) return 0;
        return (int) Math.min(Integer.MAX_VALUE, readyAt - Math.max(0, now));
    }
}
