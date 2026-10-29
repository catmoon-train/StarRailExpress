package org.agmas.noellesroles.game.modes.echo.visual;

import org.agmas.noellesroles.game.modes.echo.cards.EchoSignalView;
import java.util.List;
import java.util.UUID;

/** 视觉授权的纯函数；不推断方向信号背后的玩家，也不修改实体状态。 */
public final class EchoVisualState {
    public static final float INTERCEPTOR_SCALE = 1.25F;
    public static final double MAX_SHAKE = 0.018D;
    private EchoVisualState() { }

    public static boolean authorized(List<EchoSignalView> signals, UUID target, long now) {
        return authorized(signals, target, now, true);
    }

    /** 倒地者仍可看队友，但不会因此额外保留攻击型追踪授权。 */
    public static boolean authorized(List<EchoSignalView> signals, UUID target, long now, boolean standing) {
        if (target == null) return false;
        return signals.stream().anyMatch(signal -> target.equals(signal.target()) && now < signal.until()
                && (standing || "teammate".equals(signal.kind()))
                && !"mask".equals(signal.kind()) && !"ally".equals(signal.kind())
                && !EchoSignalView.directionOnly(signal.kind()));
    }

    /** 中立投机客各自完成私人目标，不因枚举值相同被误认成队友。 */
    public static boolean teammates(org.agmas.noellesroles.game.modes.echo.state.EchoFaction observer,
                                    org.agmas.noellesroles.game.modes.echo.state.EchoFaction target) {
        return observer != null && observer == target
                && observer != org.agmas.noellesroles.game.modes.echo.state.EchoFaction.BROKER;
    }

    /** 只返回无方向强度，复用锁定距离；范围边缘归零。 */
    public static float pressure(double distance, double range) {
        if (!Double.isFinite(distance) || distance < 0 || range <= 0 || distance >= range) return 0F;
        double near = 1D - distance / range;
        return (float) (near * near);
    }

    /** 颤抖药水只作用于仍站立的逃脱者；暂停/非行动阶段不维持恐惧光环。 */
    public static boolean trembleEligible(org.agmas.noellesroles.game.modes.echo.state.EchoFaction faction,
                                          boolean standing, boolean actionsRunning, float pressure) {
        return faction == org.agmas.noellesroles.game.modes.echo.state.EchoFaction.CARRIER
                && standing && actionsRunning && Float.isFinite(pressure) && pressure > 0F;
    }

    /** 每帧由绝对时间采样，多个杀手只取最大强度，不做累加积分。 */
    public static double shake(float pressure, double ticks, boolean vertical) {
        double strength = Float.isFinite(pressure) ? Math.max(0D, Math.min(1D, pressure)) : 0D;
        return Math.sin(ticks * (vertical ? 2.13D : 1.71D)) * MAX_SHAKE * strength * (vertical ? 0.65D : 1D);
    }
}
