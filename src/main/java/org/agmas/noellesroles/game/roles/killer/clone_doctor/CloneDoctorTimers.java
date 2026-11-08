package org.agmas.noellesroles.game.roles.killer.clone_doctor;

/** 跨职业保留的游戏时钟期限；所有时间均使用可暂停的局内 tick。 */
class CloneDoctorTimers {
    static final int CAST_TICKS = 20;
    static final int TRANSFORM_TICKS = 60 * 20;
    static final int INVISIBLE_TICKS = 10 * 20;
    static final int COPY_COOLDOWN = 30 * 20;
    static final int SUBSTITUTE_COOLDOWN = 500 * 20;
    static final int SLIME_COOLDOWN = 120 * 20;
    long castAt;
    long endsAt;
    long invisibleUntil;
    long copyReadyAt;
    long substituteReadyAt;
    long slimeReadyAt;

    void beginCast(long now) { castAt = now + CAST_TICKS; }
    void cancelCast() { castAt = 0; }
    void completeCopy(long now) {
        castAt = 0;
        endsAt = now + TRANSFORM_TICKS;
        invisibleUntil = now + INVISIBLE_TICKS;
        copyReadyAt = 0;
    }
    void stopCopy(long now) {
        castAt = 0;
        endsAt = 0;
        copyReadyAt = now + COPY_COOLDOWN;
        // 恢复本体后开始30秒冷却；不移除10秒禁伤窗口或重置其它技能冷却。
    }
    void substituteCreated(long now) { substituteReadyAt = now + SUBSTITUTE_COOLDOWN; }
    void slimeBought(long now) { slimeReadyAt = now + SLIME_COOLDOWN; }
    static int remaining(long deadline, long now) {
        if (deadline <= now) return 0;
        return (int) Math.min(Integer.MAX_VALUE, deadline - now);
    }
}
