package org.agmas.noellesroles.game.fake_steve;

/** Pure observation state machine shared by client rendering and server tests. */
public final class FakeSteveApparitionLifecycle {
    public static final int OBSERVE_TICKS = 5;
    public static final int LOOK_AWAY_TICKS = 3;
    public static final int TIMEOUT_TICKS = 30 * 20;

    public enum Stage {
        UNSEEN,
        OBSERVED,
        LOOKED_AWAY,
        TIMED_OUT
    }

    private Stage stage = Stage.UNSEEN;
    private int ageTicks;
    private int visibleTicks;
    private int lostTicks;

    public Stage tick(boolean visible, int elapsedTicks) {
        if (terminal()) {
            return stage;
        }
        int elapsed = Math.max(0, elapsedTicks);
        ageTicks += elapsed;
        if (ageTicks >= TIMEOUT_TICKS) {
            stage = Stage.TIMED_OUT;
            return stage;
        }
        if (stage == Stage.UNSEEN) {
            visibleTicks = visible ? visibleTicks + elapsed : 0;
            if (visibleTicks >= OBSERVE_TICKS) {
                stage = Stage.OBSERVED;
            }
            return stage;
        }
        lostTicks = visible ? 0 : lostTicks + elapsed;
        if (lostTicks >= LOOK_AWAY_TICKS) {
            stage = Stage.LOOKED_AWAY;
        }
        return stage;
    }

    public Stage stage() {
        return stage;
    }

    public boolean shouldReplace() {
        return stage == Stage.LOOKED_AWAY;
    }

    public boolean terminal() {
        return stage == Stage.LOOKED_AWAY || stage == Stage.TIMED_OUT;
    }
}
