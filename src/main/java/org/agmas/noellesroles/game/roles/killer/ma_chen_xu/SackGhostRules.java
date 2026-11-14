package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

/** 布袋鬼与诡客的纯数值规则；不初始化物品或世界注册表。 */
public final class SackGhostRules {
    public static final int MAX_ART_ITEMS = 4;
    public static final int CAST_INTERVAL = 3 * 20;
    public static final int REALM_CAST_INTERVAL = 10 * 20;

    public static int castInterval(boolean realmActive) { return realmActive ? REALM_CAST_INTERVAL : CAST_INTERVAL; }
    public static final int REALM_COOLDOWN = 120 * 20;
    public static final int REALM_ATTACK_INTERVAL = 20;
    public static final double REALM_ATTACK_RANGE = 3.3;
    public static final int RELIC_WARD_COOLDOWN = 45 * 20;
    public static final int RELIC_BREAK_COOLDOWN = 60 * 20;
    public static final int RELIC_BREAK_COST = 175;
    public static final double RELIC_RANGE = 20;
    public static final int WARD_DURATION = 6 * 20;
    public static final double EROSION_MULTIPLIER = 1.3;
    public static final int ESCAPE_DURATION = 12 * 20;
    public static final int ESCAPE_COOLDOWN = 150 * 20;
    public static final int SHROUD_DURATION = 15 * 20;
    public static final int SHROUD_COOLDOWN = 65 * 20;
    public static final int ECHO_WINDOW = 8 * 20;
    public static final int VEIL_DURATION = 3 * 20;
    public static final int WAIL_DURATION = 5 * 20;
    public static final int PARASITE_DURATION = 15 * 20;
    public static final double THREAT_RANGE = 15;
    public static final double VEIL_RANGE = 12, VEIL_RADIUS = 5;
    public static final double PUSH_RADIUS = 7, PUSH_DISTANCE = 9;
    public static final double ECHO_DISTANCE = 12, ECHO_RADIUS = 4;
    public static final double WAIL_RADIUS = 10, PARASITE_RANGE = 12, SEIZE_RANGE = 15;
    public static final int VEIL_SAN = 32, PUSH_SAN = 20, EFFIGY_SAN = 35, ECHO_SAN = 24;
    public static final int WAIL_SAN = 8, PARASITE_SAN = 5, SEIZE_SAN = 30;

    private SackGhostRules() {}

    public static int generationCooldownSeconds(int initialPlayers) {
        return Math.max(10, 55 - Math.max(0, initialPlayers));
    }

    public static int stageThreshold(int stage, int initialPlayers) {
        int n = Math.clamp(initialPlayers, 1, 128);
        return switch (stage) {
            case 2 -> Math.max(180, 16 * n);
            case 3 -> Math.max(440, 36 * n);
            case 4 -> Math.max(800, 64 * n);
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

    public static int realmAttackSan(int stage) { return stage >= 4 ? 70 : 50; }

    /** 中立与大妖精这类没有真实SAN的目标改扣虚拟血量，保证每两次命中可清空默认20点虚拟血量。 */
    public static boolean usesVirtualHealth(boolean neutral, boolean fakeSan) { return neutral || fakeSan; }

    public static int realmAttackVirtual(int stage) { return Math.max(1, toVirtualDamage(realmAttackSan(stage))); }

    public static int toVirtualDamage(int sanDamage) { return Math.max(1, sanDamage * 2 / 10); }

    public static int realmDuration(int stage) { return stage >= 4 ? 800 : 600; }

    public static boolean consumesOnCast(GhostArt art, boolean realmActive) {
        return art != GhostArt.SHROUD && !realmActive;
    }

    public static boolean parasiteShouldExecute(boolean glitchRobot, double sanFraction) {
        return glitchRobot || isCondemned(sanFraction);
    }

    public static int wailSlowAmplifier(int exposureTicks) {
        return Math.clamp(exposureTicks / 40, 0, 2);
    }

    public static int gunThreat(boolean eligible, double distanceSquared, boolean carried, boolean held) {
        if (!eligible || !Double.isFinite(distanceSquared) || distanceSquared < 0
                || distanceSquared > THREAT_RANGE * THREAT_RANGE) return 0;
        return held ? 2 : carried ? 1 : 0;
    }

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
