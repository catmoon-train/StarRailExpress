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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveWanderPolicyTest {
    @Test
    void taskPointsMustBeFarFromTheLastSpotAndTheRecentPlate() {
        assertTrue(FakeSteveWanderPolicy.isUsableTaskPoint(81.0D, false, false));
        assertFalse(FakeSteveWanderPolicy.isUsableTaskPoint(16.0D, false, false));
        assertFalse(FakeSteveWanderPolicy.isUsableTaskPoint(200.0D, true, false));
        assertFalse(FakeSteveWanderPolicy.isUsableTaskPoint(200.0D, false, true));
    }

    @Test
    void socialStandsStayAFewBlocksAwayFromAnotherPlayer() {
        assertTrue(FakeSteveWanderPolicy.isSocialStand(64.0D));
        assertFalse(FakeSteveWanderPolicy.isSocialStand(4.0D));
        assertFalse(FakeSteveWanderPolicy.isSocialStand(144.0D));
    }

    @Test
    void aMissingOrFailedRouteReselectsImmediately() {
        assertTrue(FakeSteveWanderPolicy.shouldReselectNow(true, 0, false));
        assertFalse(FakeSteveWanderPolicy.shouldReselectNow(false, 2, false));
        assertTrue(FakeSteveWanderPolicy.shouldReselectNow(false, 4, false));
        assertTrue(FakeSteveWanderPolicy.shouldReselectNow(false, 0, true));
        assertFalse(FakeSteveWanderPolicy.shouldReselectNow(false, 1, false));
    }
}
