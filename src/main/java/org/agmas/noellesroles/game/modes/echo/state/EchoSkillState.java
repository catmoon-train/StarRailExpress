package org.agmas.noellesroles.game.modes.echo.state;

import java.util.Objects;

/** 单个成员的技能冷却与挂起/增益窗口；只接受比赛 tick，暂停不推进。 */
public final class EchoSkillState {
    private long readyAt;
    private long buffUntil;
    private long pendingUntil;

    public long readyAt() { return readyAt; }
    public long buffUntil() { return buffUntil; }
    public long pendingUntil() { return pendingUntil; }

    public boolean ready(long now) { return now >= readyAt; }
    public boolean buffActive(EchoSkill skill, long now) { return skill != null && !skill.pending() && now < buffUntil; }
    public boolean pendingActive(EchoSkill skill, long now) { return skill != null && skill.pending() && now < pendingUntil; }

    /** 释放成功才进入冷却并开启对应窗口；失败返回 false 且不改状态。 */
    public boolean activate(EchoSkill skill, long now) {
        Objects.requireNonNull(skill);
        if (!ready(now)) return false;
        readyAt = Math.addExact(now, skill.cooldownTicks());
        if (skill.pending()) pendingUntil = Math.addExact(now, skill.windowTicks());
        else buffUntil = Math.addExact(now, skill.windowTicks());
        return true;
    }

    /** 一次性效果在真正生效时消耗，到期未用自动失效且不退冷却。 */
    public boolean consumePending(EchoSkill skill, long now) {
        if (!pendingActive(skill, now)) return false;
        pendingUntil = now;
        return true;
    }

    public void expire(long now) {
        if (now >= buffUntil) buffUntil = 0;
        if (now >= pendingUntil) pendingUntil = 0;
    }

    public void clear() { readyAt = 0; buffUntil = 0; pendingUntil = 0; }
}
