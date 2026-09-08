/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void idleBodiesEatOnceThenLeaveThePlateAlone() {
        assertTrue(FakeSteveInteractionPolicy.isHungry(10));
        assertFalse(FakeSteveInteractionPolicy.isHungry(16));
        assertTrue(FakeSteveInteractionPolicy.shouldSnack(true, false, false));
        assertFalse(FakeSteveInteractionPolicy.shouldSnack(true, true, false));
        assertTrue(FakeSteveInteractionPolicy.shouldSnack(false, false, true));
        assertTrue(FakeSteveInteractionPolicy.shouldTakeFromPlate(false, false, false, true));
        assertFalse(FakeSteveInteractionPolicy.shouldTakeFromPlate(true, false, false, true));
        assertFalse(FakeSteveInteractionPolicy.shouldTakeFromPlate(false, false, true, true));
        assertFalse(FakeSteveInteractionPolicy.shouldTakeFromPlate(false, true, false, true));
    }
}
