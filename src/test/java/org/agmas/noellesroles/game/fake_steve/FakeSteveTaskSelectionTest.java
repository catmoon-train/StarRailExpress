package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeSteveTaskSelectionTest {
    @Test
    void keepsAReachableTaskWhileItIsMakingTimelyProgress() {
        Task selected = FakeSteveTaskSelection.choose(
                List.of(new FakeSteveTaskSelection.Candidate(Task.SLEEP, 20.0D, false),
                        new FakeSteveTaskSelection.Candidate(Task.CHAIR, 5.0D, false)),
                Task.SLEEP, 100L, 500L);

        assertEquals(Task.SLEEP, selected);
    }

    @Test
    void switchesToAnotherTaskAfterTheCurrentOneStalls() {
        Task selected = FakeSteveTaskSelection.choose(
                List.of(new FakeSteveTaskSelection.Candidate(Task.SLEEP, 20.0D, false),
                        new FakeSteveTaskSelection.Candidate(Task.CHAIR, 5.0D, false)),
                Task.SLEEP, 100L, 1_101L);

        assertEquals(Task.CHAIR, selected);
    }
}
