package org.agmas.noellesroles.game.fake_steve;

/**
 * Stateful, Minecraft-independent decision module for one possessed body.
 * Callers provide observations; the returned intent is the only decision
 * surface consumed by the world adapter.
 */
public final class FakeSteveBrain {
    public static final int LOOK_AWAY_TICKS = 10;

    private AgentMode mode = AgentMode.DISGUISE_IDLE;
    private boolean armed;
    private int lookAwayTicks;

    public BrainIntent tick(PerceptionSnapshot snapshot) {
        if (snapshot.recovering()) {
            mode = AgentMode.RECOVER;
            return intent(false, false, false, false, false, true);
        }

        if (snapshot.engagementTriggered()) {
            armed = true;
            lookAwayTicks = 0;
            mode = AgentMode.STARE;
        }

        if (armed && !snapshot.focusValid()) {
            armed = false;
            lookAwayTicks = 0;
        }

        if (armed) {
            if (snapshot.targetLookingAtFake()) {
                lookAwayTicks = 0;
                mode = AgentMode.STARE;
                return intent(true, false, false, false, false, false);
            }
            lookAwayTicks += Math.max(0, snapshot.elapsedTicks());
            if (lookAwayTicks >= LOOK_AWAY_TICKS) {
                mode = AgentMode.STALK;
                return intent(false, true, snapshot.safeBackstab(), false, false, false);
            }
            mode = AgentMode.STARE;
            return intent(true, false, false, false, false, false);
        }

        if (snapshot.assimilationReady()) {
            mode = AgentMode.ASSIMILATE;
            return intent(false, true, false, true, false, false);
        }
        if (snapshot.taskAvailable()) {
            mode = AgentMode.DISGUISE_TASK;
            return intent(false, false, false, false, true, false);
        }
        mode = AgentMode.DISGUISE_IDLE;
        return intent(false, false, false, false, false, false);
    }

    public AgentMode mode() {
        return mode;
    }

    /** Drops the current engagement so the body stops lingering on one behaviour. */
    public void disengage() {
        armed = false;
        lookAwayTicks = 0;
        mode = AgentMode.DISGUISE_IDLE;
    }

    private BrainIntent intent(boolean holdPosition, boolean followTarget, boolean attack,
            boolean assimilate, boolean performTask, boolean recover) {
        return new BrainIntent(mode, holdPosition, followTarget, attack, assimilate,
                performTask, recover);
    }

    public record PerceptionSnapshot(int elapsedTicks, boolean recovering,
            boolean engagementTriggered, boolean focusValid,
            boolean targetLookingAtFake, boolean safeBackstab,
            boolean assimilationReady, boolean taskAvailable) {
    }

    public record BrainIntent(AgentMode mode, boolean holdPosition,
            boolean followTarget, boolean attack, boolean assimilate,
            boolean performTask, boolean recover) {
    }
}
