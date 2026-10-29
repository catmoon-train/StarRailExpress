package io.wifi.starrailexpress.settlement;

/** 不依赖世界的单观众状态机；时间只由服务端 tick 推进。 */
public final class SettlementState {
    public enum Phase { BLACKOUT, LOADING, PLAYING, CLOSED }
    private Phase phase = Phase.BLACKOUT;
    private int age;
    private int played;
    private int holdStarted = -1;
    private int lastHold = -100;

    public Phase phase() { return phase; }
    public int played() { return played; }
    public boolean blackReady() {
        if (phase != Phase.BLACKOUT) return false;
        phase = Phase.LOADING; age = 0; return true;
    }
    public boolean ready() {
        if (phase != Phase.LOADING) return false;
        phase = Phase.PLAYING; age = 0; return true;
    }
    public void hold() {
        if (phase != Phase.PLAYING || played < SettlementSnapshot.SKIP_AFTER_TICKS) return;
        if (holdStarted < 0 || played - lastHold > 8) holdStarted = played;
        lastHold = played;
    }
    public void release() { holdStarted = -1; }
    public boolean tick(int duration) {
        if (phase == Phase.CLOSED) return false;
        age++;
        if (phase != Phase.PLAYING) return age >= 600;
        played++;
        if (played - lastHold > 8) release();
        return played >= duration + 40 || (holdStarted >= 0
                && played - holdStarted >= SettlementSnapshot.HOLD_TICKS);
    }
    public boolean mayFinish(int duration) { return phase == Phase.PLAYING && played >= duration - 2; }
    public void close() { phase = Phase.CLOSED; release(); }
}
