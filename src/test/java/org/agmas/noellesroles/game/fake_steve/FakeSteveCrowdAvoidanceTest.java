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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveCrowdAvoidanceTest {
    @Test
    void playerDirectlyAheadCausesLateralAvoidanceInsteadOfWalkingIntoThem() {
        FakeSteveCrowdAvoidance.Decision decision = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0.0D, 1.0D)), 0);

        assertTrue(decision.crowded());
        assertTrue(decision.forwardScale() <= 0.4F);
        assertTrue(Math.abs(decision.strafe()) >= 0.5F);
    }

    @Test
    void playerOutsideTheMovementCorridorDoesNotCauseWobble() {
        FakeSteveCrowdAvoidance.Decision decision = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(2.0D, 0.5D)), 0);

        assertEquals(1.0F, decision.forwardScale(), 0.001F);
        assertEquals(0.0F, decision.strafe(), 0.001F);
        assertFalse(decision.crowded());
    }

    @Test
    void prolongedPlayerBlockageRequestsAPathReplan() {
        FakeSteveCrowdAvoidance.Decision decision = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0.0D, 1.0D)), 8);

        assertTrue(decision.shouldRepath());
    }

    @Test
    void tinyObstacleJitterDoesNotFlipTheChosenAvoidanceSide() {
        FakeSteveCrowdAvoidance.Decision rightJitter = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0.02D, 1.0D)), 5);
        FakeSteveCrowdAvoidance.Decision leftJitter = FakeSteveCrowdAvoidance.decide(
                0.0D, 0.0D, 0.0D, 4.0D,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(-0.02D, 1.0D)), 5);

        assertEquals(Math.signum(rightJitter.strafe()), Math.signum(leftJitter.strafe()));
    }
}
