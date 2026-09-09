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

package org.agmas.noellesroles.content.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusAilmentPolicyTest {
    @Test
    void missingLungsLevelOneSubtractsFiveSecondsFromTheBase() {
        assertEquals(115, StatusAilmentPolicy.missingLungsSeconds(0));
        assertEquals(115 * 20, StatusAilmentPolicy.missingLungsTicks(0));
    }

    @Test
    void missingLungsHigherLevelsDieFasterAndNeverGoBelowFiveSeconds() {
        assertEquals(110, StatusAilmentPolicy.missingLungsSeconds(1));
        assertEquals(5, StatusAilmentPolicy.missingLungsSeconds(40));
    }

    @Test
    void aphreniaDefaultsMatchThirtyToFortyFiveThenTenToFifteen() {
        assertEquals(30 * 20, StatusAilmentPolicy.aphreniaMinIntervalTicks(0));
        assertEquals(45 * 20, StatusAilmentPolicy.aphreniaMaxIntervalTicks(0));
        assertEquals(10 * 20, StatusAilmentPolicy.aphreniaMinDurationTicks(0));
        assertEquals(15 * 20, StatusAilmentPolicy.aphreniaMaxDurationTicks(0));
    }

    @Test
    void higherAphreniaLevelsTriggerSoonerAndLastLonger() {
        assertTrue(StatusAilmentPolicy.aphreniaMinIntervalTicks(2)
                < StatusAilmentPolicy.aphreniaMinIntervalTicks(0));
        assertTrue(StatusAilmentPolicy.aphreniaMinDurationTicks(2)
                > StatusAilmentPolicy.aphreniaMinDurationTicks(0));
        assertTrue(StatusAilmentPolicy.aphreniaMaxIntervalTicks(3)
                > StatusAilmentPolicy.aphreniaMinIntervalTicks(3));
        assertTrue(StatusAilmentPolicy.aphreniaMaxDurationTicks(3)
                > StatusAilmentPolicy.aphreniaMinDurationTicks(3));
    }

    @Test
    void intellectEpisodesStayShorterThanTheirGaps() {
        assertTrue(StatusAilmentPolicy.intellectMaxDurationTicks(0)
                < StatusAilmentPolicy.intellectMinIntervalTicks(0));
        assertTrue(StatusAilmentPolicy.intellectMaxIntervalTicks(1)
                > StatusAilmentPolicy.intellectMinIntervalTicks(1));
    }

    @Test
    void motorFallsAndTremorGrowWithPotionLevel() {
        assertTrue(StatusAilmentPolicy.motorFallChance(2)
                > StatusAilmentPolicy.motorFallChance(0));
        assertTrue(StatusAilmentPolicy.handTremorDrawAmplitude(0)
                > StatusAilmentPolicy.handTremorIdleAmplitude(0));
        assertTrue(StatusAilmentPolicy.handTremorDrawAmplitude(2)
                > StatusAilmentPolicy.handTremorDrawAmplitude(0));
        assertTrue(StatusAilmentPolicy.myopiaFocusBlocks(2)
                < StatusAilmentPolicy.myopiaFocusBlocks(0));
    }

    @Test
    void pickRangeStaysInclusive() {
        assertEquals(10, StatusAilmentPolicy.pickRange(10, 10, 99));
        int value = StatusAilmentPolicy.pickRange(30, 45, 7);
        assertTrue(value >= 30 && value <= 45);
    }
}
