package org.agmas.noellesroles.game.fake_steve;

/** 当前局内的手控时钟；只接受服务端游戏 tick，不借用原职业的冷却组件。 */
public final class FakeSteveManualControl {
    private long endsAtTick = -1L;
    private long readyAtTick;

    public boolean isActive(long now) {
        return endsAtTick >= 0L && now < endsAtTick;
    }

    public boolean hasControl() {
        return endsAtTick >= 0L;
    }

    public long remainingTicks(long now) {
        return Math.max(0L, endsAtTick - now);
    }

    public long cooldownTicks(long now) {
        return Math.max(0L, readyAtTick - now);
    }

    public boolean start(long now) {
        if (hasControl() || cooldownTicks(now) > 0L) {
            return false;
        }
        endsAtTick = now + FakeSteveRules.MANUAL_CONTROL_TICKS;
        return true;
    }

    /** 自然结束从精确截止 tick 起算；会议、死亡等提前取消则从取消 tick 起算 80 秒。 */
    public boolean finish(long now) {
        if (!hasControl()) {
            return false;
        }
        readyAtTick = Math.min(now, endsAtTick) + FakeSteveRules.MANUAL_CONTROL_COOLDOWN_TICKS;
        endsAtTick = -1L;
        return true;
    }
}
