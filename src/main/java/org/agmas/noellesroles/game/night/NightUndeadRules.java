package org.agmas.noellesroles.game.night;

/** 亡灵来源迁移、近距警戒与出生点约束；不依赖世界，供实体与独立测试共用。 */
public final class NightUndeadRules {
    public enum Source { LORD, HALLOWEEN, NIGHT, WILD }

    public static final double ALERT_RADIUS = 12.0;
    public static final double ROAM_RADIUS = 8.0;
    /** 允许从漫游边缘短途追击，但不能被持续引离出生区域。 */
    public static final double LEASH_RADIUS = ROAM_RADIUS + ALERT_RADIUS;

    private NightUndeadRules() {}

    /** 黑夜亡灵可持续整局，延命道具不能让接近上限的寿命溢出为负。 */
    public static int extendLifetime(int remaining, int extra) {
        return (int) Math.clamp((long) remaining + extra, 0L, (long) Integer.MAX_VALUE);
    }

    /** 老存档优先保留活动标记；只有普通且有主人的旧亡灵迁移为领主召唤。 */
    public static Source restoreSource(String saved, boolean halloween, boolean hasOwner) {
        try {
            return Source.valueOf(saved);
        } catch (IllegalArgumentException ignored) {
            return halloween ? Source.HALLOWEEN : hasOwner ? Source.LORD : Source.WILD;
        }
    }

    /** 非领主的自然目标和临时指令必须走同一个距离限制；包含三维距离。 */
    public static boolean canEngage(Source source, double targetDistanceSqr,
                                    double selfAnchorDistanceSqr, double targetAnchorDistanceSqr) {
        return source == Source.LORD || targetDistanceSqr >= 0
                && targetDistanceSqr <= ALERT_RADIUS * ALERT_RADIUS
                && selfAnchorDistanceSqr >= 0 && selfAnchorDistanceSqr <= LEASH_RADIUS * LEASH_RADIUS
                && targetAnchorDistanceSqr >= 0 && targetAnchorDistanceSqr <= LEASH_RADIUS * LEASH_RADIUS;
    }

    /** 漫游点应留在出生点球体内，避免无重力随机走动无限漂移。 */
    public static boolean isWithinRoam(double anchorDistanceSqr) {
        return anchorDistanceSqr >= 0 && anchorDistanceSqr <= ROAM_RADIUS * ROAM_RADIUS;
    }
}
