package org.agmas.noellesroles.game.modes.echo.runtime;

import org.agmas.noellesroles.game.modes.echo.state.EchoPhase;

/**
 * 只消费外部计时器的 tick 边界，不自行扣减阶段时间。
 * START=1、END=0 的最后一个有效 tick 仍能完成目标并切换阶段。
 */
public final class EchoRoundClock {
    public enum Boundary { NONE, OPERATIONS_STARTED, TIME_EXPIRED }

    private EchoPhase phase = EchoPhase.SAFE;
    private long matchTick;
    private long capturedSequence = Long.MIN_VALUE;
    private long processedSequence = Long.MIN_VALUE;
    private int startRemaining;
    private boolean frozen;
    private boolean captured;
    private boolean changed;

    public EchoPhase phase() { return phase; }
    public long matchTick() { return matchTick; }

    public void capture(long sequence, int remaining, boolean paused) {
        if (sequence < 0 || remaining < 0) throw new IllegalArgumentException("无效计时边界");
        if (captured && sequence <= capturedSequence) throw new IllegalStateException("重复或乱序 START");
        captured = true;
        capturedSequence = sequence;
        startRemaining = remaining;
        frozen = paused;
        changed = false;
    }

    public boolean canProcess(boolean pausedNow) {
        return captured && processedSequence != capturedSequence && !frozen && !pausedNow
                && startRemaining > 0 && phase != EchoPhase.SETTLING && phase != EchoPhase.FINISHED;
    }

    /** 只有当前 tick 原本有时间时，最后信标完成才可开启完整撤离窗。 */
    public boolean openEvacuation(boolean pausedNow) {
        if (!canProcess(pausedNow) || phase != EchoPhase.OPERATIONS) return false;
        phase = EchoPhase.EVACUATION;
        changed = true;
        return true;
    }

    public Boundary finish(int remainingNow, boolean pausedNow) {
        if (remainingNow < 0) throw new IllegalArgumentException("剩余时间不能为负");
        if (!captured || processedSequence == capturedSequence) return Boundary.NONE;
        processedSequence = capturedSequence;
        if (frozen || pausedNow || phase == EchoPhase.SETTLING || phase == EchoPhase.FINISHED) return Boundary.NONE;
        if (startRemaining > 0) matchTick++;
        if (changed || remainingNow > 0) return Boundary.NONE;
        if (phase == EchoPhase.SAFE) {
            phase = EchoPhase.OPERATIONS;
            return Boundary.OPERATIONS_STARTED;
        }
        return Boundary.TIME_EXPIRED;
    }

    public void settle() { phase = EchoPhase.SETTLING; }
    public void finishMatch() { phase = EchoPhase.FINISHED; }
}
