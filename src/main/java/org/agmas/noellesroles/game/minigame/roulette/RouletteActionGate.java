package org.agmas.noellesroles.game.minigame.roulette;

/** 关键帧与最终结算的单次提交门；与帧率、网络重发、调度回调无关。 */
public final class RouletteActionGate {
    private RoulettePhase phase = RoulettePhase.WAITING;
    private long started;
    private boolean committed, settled;
    public void begin(RoulettePhase phase, long started) { this.phase = phase; this.started = started; committed = false; }
    public boolean claimImpact(long now) {
        if (phase.impact() < 0 || committed || now - started < phase.impact()) return false;
        committed = true;
        return true;
    }
    public boolean claimSettlement() {
        if (settled || phase != RoulettePhase.ENDING) return false;
        settled = true;
        return true;
    }
    public void reset() { phase = RoulettePhase.WAITING; committed = false; settled = false; }
}
