package org.agmas.noellesroles.game.modes.echo.rules;

/** 不可热改的模式基线；服务器时间统一以暂停感知的逻辑 tick 表示。 */
public final class EchoRules {
    public static final int TICKS_PER_SECOND = 20;
    public static final int SAFE_TICKS = 400;
    private EchoRules() { }

    public static int secondsToTicks(int seconds) {
        if (seconds < 0) throw new IllegalArgumentException("秒数不能为负");
        return Math.multiplyExact(seconds, TICKS_PER_SECOND);
    }
}
