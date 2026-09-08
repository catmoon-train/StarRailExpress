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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveApparitionLifecycleTest {

    @Test
    void apparitionMustBeObservedBeforeLookingAwayCanReplace() {
        FakeSteveApparitionLifecycle lifecycle = new FakeSteveApparitionLifecycle();

        lifecycle.tick(false, 3);
        assertEquals(FakeSteveApparitionLifecycle.Stage.UNSEEN, lifecycle.stage());

        lifecycle.tick(true, 5);
        assertEquals(FakeSteveApparitionLifecycle.Stage.OBSERVED, lifecycle.stage());

        lifecycle.tick(false, 3);
        assertEquals(FakeSteveApparitionLifecycle.Stage.LOOKED_AWAY, lifecycle.stage());
        assertTrue(lifecycle.shouldReplace());
    }

    @Test
    void staringForThirtySecondsEndsTheApparitionHarmlessly() {
        FakeSteveApparitionLifecycle lifecycle = new FakeSteveApparitionLifecycle();

        lifecycle.tick(true, FakeSteveApparitionLifecycle.TIMEOUT_TICKS);

        assertEquals(FakeSteveApparitionLifecycle.Stage.TIMED_OUT, lifecycle.stage());
        assertFalse(lifecycle.shouldReplace());
    }
}
