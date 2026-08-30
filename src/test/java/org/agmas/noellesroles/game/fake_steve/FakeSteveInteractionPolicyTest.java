package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveInteractionPolicyTest {
    @Test
    void chairsRequireTheSameCloseRangeAsHumanMounting() {
        assertEquals(1.4D, FakeSteveInteractionPolicy.maxInteractionDistance(Task.CHAIR), 0.001D);
        assertEquals(1.4D, FakeSteveInteractionPolicy.maxInteractionDistance(Task.TOILET), 0.001D);
    }

    @Test
    void eatingAndDrinkingMaintainVanillaUseUntilTheAnimationCompletes() {
        assertTrue(FakeSteveInteractionPolicy.maintainsUseAnimation(Task.EAT));
        assertTrue(FakeSteveInteractionPolicy.maintainsUseAnimation(Task.DRINK));
    }

    @Test
    void worldInteractionsAlwaysExposeAHandSwing() {
        assertTrue(FakeSteveInteractionPolicy.swingsHand(Task.CHAIR));
        assertTrue(FakeSteveInteractionPolicy.swingsHand(Task.SLEEP));
        assertTrue(FakeSteveInteractionPolicy.swingsHand(Task.NOTE_BLOCK));
        assertTrue(FakeSteveInteractionPolicy.swingsHand(Task.EAT));
    }

    @Test
    void completedSeatAndSleepTasksReleaseTheirPersistentPosture() {
        assertTrue(FakeSteveInteractionPolicy.releasesPostureAfterCompletion(Task.CHAIR));
        assertTrue(FakeSteveInteractionPolicy.releasesPostureAfterCompletion(Task.TOILET));
        assertTrue(FakeSteveInteractionPolicy.releasesPostureAfterCompletion(Task.SLEEP));
        assertTrue(FakeSteveInteractionPolicy.releasesPostureAfterCompletion(Task.RAED_BOOK));
    }
}
