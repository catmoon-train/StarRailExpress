package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveBrainTest {

    @Test
    void disguiseTaskWinsOverAnOptionalHunt() {
        FakeSteveBrain brain = new FakeSteveBrain();

        FakeSteveBrain.BrainIntent intent = brain.tick(snapshot(false, false, false,
                false, false, true));

        assertEquals(AgentMode.DISGUISE_TASK, intent.mode());
        assertTrue(intent.performTask());
        assertFalse(intent.attack());
    }

    @Test
    void communicationArmsAStareAndLookingAwayStartsTheStalk() {
        FakeSteveBrain brain = new FakeSteveBrain();

        FakeSteveBrain.BrainIntent armed = brain.tick(snapshot(true, true, true,
                false, false, false));
        assertEquals(AgentMode.STARE, armed.mode());
        assertTrue(armed.holdPosition());

        FakeSteveBrain.BrainIntent intent = armed;
        for (int i = 0; i < 2; i++) {
            intent = brain.tick(snapshot(false, true, false,
                    false, false, false));
        }

        assertEquals(AgentMode.STALK, intent.mode());
        assertTrue(intent.followTarget());
    }

    @Test
    void lookingBackFreezesAnArmedStalkerAndSafeBackstabKills() {
        FakeSteveBrain brain = new FakeSteveBrain();
        brain.tick(snapshot(true, true, true, false, false, false));
        brain.tick(snapshot(false, true, false, false, false, false));
        brain.tick(snapshot(false, true, false, false, false, false));

        FakeSteveBrain.BrainIntent lookingBack = brain.tick(snapshot(false, true, true,
                false, false, false));
        assertEquals(AgentMode.STARE, lookingBack.mode());
        assertTrue(lookingBack.holdPosition());

        brain.tick(snapshot(false, true, false, false, false, false));
        brain.tick(snapshot(false, true, false, false, false, false));
        FakeSteveBrain.BrainIntent attack = brain.tick(snapshot(false, true, false,
                true, false, false));
        assertEquals(AgentMode.STALK, attack.mode());
        assertTrue(attack.attack());
    }

    private static FakeSteveBrain.PerceptionSnapshot snapshot(boolean engagement,
            boolean focusValid, boolean targetLooking, boolean safeBackstab,
            boolean assimilationReady, boolean taskAvailable) {
        return new FakeSteveBrain.PerceptionSnapshot(5, false, engagement, focusValid,
                targetLooking, safeBackstab, assimilationReady, taskAvailable);
    }
}
