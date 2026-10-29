package org.agmas.noellesroles.game.modes.echo.combat;

/**
 * 追猎刀的蓄力释放与受击击退窗口。
 *
 * <p>固定蓄力 0.8 秒，松手即提交出击，没有额外前摇。3 秒出击冷却与 6 秒命中冷却
 * 仍由 {@link EchoCombatState} 持有。
 */
public final class EchoCutterStrike {
    private EchoCutterStrike() { }

    /** 客户端动作与服务端校验共享的固定蓄力时长。 */
    public static final int CHARGE_TICKS = 16;

    /** 水平短冲量（格/tick）。原版 knockback(0.4) 约 0.2，这里略强但仍是短推，不传送。 */
    public static final double KNOCKBACK_STRENGTH = 0.28D;
    /** 倒地后仍允许被动推力的比赛 tick；同 tick / 下一 tick 的移动冻结不得提前清零。 */
    public static final int KNOCKBACK_WINDOW_TICKS = 4;

    public record Impulse(double x, double z, long until) { }

    /** 蓄满后松手提交；未蓄满不消耗冷却。 */
    public static boolean commit(EchoCombatState state, long now) {
        return commit(state,now,now);
    }

    /** 队列等待不补蓄力时间，冷却仍从实际出击 tick 开始。 */
    public static boolean commit(EchoCombatState state, long now, long releasedAt) {
        return state != null && state.releaseCutter(now, CHARGE_TICKS, releasedAt);
    }


    /**
     * 只有服务器实际伤害成功才有受击反馈。友军复位、忽略、强制淘汰都不发。
     */
    public static boolean feedbackEligible(EchoCombatState.Hit hit, boolean force, boolean environmental) {
        return !force && !environmental && (hit == EchoCombatState.Hit.DAMAGED
                || hit == EchoCombatState.Hit.DOWNED || hit == EchoCombatState.Hit.ELIMINATED);
    }

    /** 攻击者朝受击者的水平单位方向；垂直重合时不推。 */
    public static double[] horizontal(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX, dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) return new double[]{0D, 0D};
        return new double[]{dx / length * KNOCKBACK_STRENGTH, dz / length * KNOCKBACK_STRENGTH};
    }

    public static Impulse impulse(long now, double fromX, double fromZ, double toX, double toZ) {
        double[] push = horizontal(fromX, fromZ, toX, toZ);
        if (push[0] == 0D && push[1] == 0D) return null;
        return new Impulse(push[0], push[1], now + KNOCKBACK_WINDOW_TICKS);
    }

    /** 暂停、到期、断线或换世界后不得继续推进。 */
    public static boolean pushing(Impulse impulse, long now, boolean paused) {
        return impulse != null && !paused && now < impulse.until();
    }
}
