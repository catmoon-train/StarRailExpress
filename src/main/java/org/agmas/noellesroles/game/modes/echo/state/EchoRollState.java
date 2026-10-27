package org.agmas.noellesroles.game.modes.echo.state;

/** 翻滚的会话权威状态；比赛 tick 暂停时不推进，取消动作不返还冷却。 */
public final class EchoRollState {
    public static final int COOLDOWN_TICKS = 600;
    public static final int DURATION_TICKS = 8;
    public static final double DISTANCE = 3;
    private long readyAt, until;
    public long readyAt() { return readyAt; }
    public long until() { return until; }
    public boolean active(long now) { return now >= 0 && now < until; }
    public boolean ready(long now) { return now >= 0 && now >= readyAt && !active(now); }
    public boolean start(long now) {
        if (!ready(now)) return false;
        readyAt = now + COOLDOWN_TICKS; until = now + DURATION_TICKS;
        return true;
    }
    public void cancel() { until = 0; }
    public void clear() { readyAt = 0; until = 0; }
    /** 输入只决定方向；客户端不能提供距离、速度、时长或冷却。 */
    public static boolean validDirection(double x, double z) {
        double length = x * x + z * z;
        return Double.isFinite(x) && Double.isFinite(z) && length >= 0.25 && length <= 1.01;
    }
}
