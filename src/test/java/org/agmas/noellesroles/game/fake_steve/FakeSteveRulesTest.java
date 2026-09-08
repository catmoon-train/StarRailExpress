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

class FakeSteveRulesTest {

    @Test
    void highSanityAwayFromBodiesIsNotAnApparitionCandidate() {
        assertEquals(0, FakeSteveRules.apparitionRisk(70, 0));
        assertEquals(0, FakeSteveRules.apparitionRisk(100, 0));
    }

    @Test
    void lowSanityAndBodiesIncreaseApparitionRisk() {
        assertEquals(1, FakeSteveRules.apparitionRisk(69, 0));
        assertEquals(50, FakeSteveRules.apparitionRisk(100, 2));
        assertEquals(80, FakeSteveRules.apparitionRisk(40, 2));
    }

    @Test
    void fakeSteveWinsOnlyWhenLivingShareIsStrictlyGreaterThanSixtyPercent() {
        assertFalse(FakeSteveRules.hasWon(6, 10));
        assertTrue(FakeSteveRules.hasWon(7, 10));
        assertFalse(FakeSteveRules.hasWon(3, 5));
        assertTrue(FakeSteveRules.hasWon(4, 5));
    }

    @Test
    void thresholdStartsAHuntAndOnlyHumanExtinctionDeclaresItsVictory() {
        assertTrue(FakeSteveRules.shouldStartHunt(7, 10));
        assertFalse(FakeSteveRules.shouldDeclareHuntVictory(1));
        assertTrue(FakeSteveRules.shouldDeclareHuntVictory(0));
    }

    @Test
    void huntRoomRecallRunsEveryNinetySeconds() {
        assertFalse(FakeSteveRules.shouldRecallHuntPlayers(1799, 1800));
        assertTrue(FakeSteveRules.shouldRecallHuntPlayers(1800, 1800));
        assertTrue(FakeSteveRules.shouldRecallHuntPlayers(1801, 1800));
    }

    @Test
    void assimilationRequiresTwoFakesNoOtherHumanAndThreeSeconds() {
        assertFalse(FakeSteveRules.canAssimilate(1, 0, 60));
        assertFalse(FakeSteveRules.canAssimilate(2, 1, 60));
        assertFalse(FakeSteveRules.canAssimilate(2, 0, 59));
        assertTrue(FakeSteveRules.canAssimilate(2, 0, 60));
    }

    @Test
    void faceToFaceCommunicationRequiresFiveContinuousSeconds() {
        assertFalse(FakeSteveRules.hasFaceToFaceCommunication(99));
        assertTrue(FakeSteveRules.hasFaceToFaceCommunication(100));
    }
}
