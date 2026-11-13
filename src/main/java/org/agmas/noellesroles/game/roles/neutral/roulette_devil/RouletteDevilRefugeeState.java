package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

/** 难民亡命徒的临时离场状态；只有阶段中被压制的存活恶魔才有复活资格。 */
public final class RouletteDevilRefugeeState {
    public enum Action { NONE, SUSPEND, RESUME }
    private boolean pending;
    private int returnGameType = 2;

    public boolean pending() { return pending; }
    public int returnGameType() { return returnGameType; }

    public Action update(boolean running, boolean refugeeActive, boolean alive, boolean spectator, int gameType) {
        if (!running) {
            reset();
            return Action.NONE;
        }
        if (refugeeActive) {
            if (spectator || !alive && !pending) return Action.NONE;
            if (!pending) {
                returnGameType = Math.clamp(gameType, 0, 2);
                pending = true;
            }
            // 外部复活不能提前解除压制，也不能用旁观模式覆盖原游戏模式。
            return Action.SUSPEND;
        }
        if (!pending) return Action.NONE;
        pending = false;
        return spectator ? Action.RESUME : Action.NONE;
    }
    public void load(boolean pending, int gameType) {
        this.pending = pending;
        returnGameType = Math.clamp(gameType, 0, 2);
    }
    public void reset() { pending = false; returnGameType = 2; }
}
