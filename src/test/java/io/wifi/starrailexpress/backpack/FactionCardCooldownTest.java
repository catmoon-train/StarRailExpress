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

package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionCardCooldownTest {
    @Test
    void defaultIntervalIsFortyFiveMinutes() {
        assertEquals(45L * 60_000L, FactionCardCooldown.durationMs(true, 45));
    }

    @Test
    void disabledOrZeroMinutesMeansNoCooldown() {
        assertEquals(0L, FactionCardCooldown.durationMs(false, 45));
        assertEquals(0L, FactionCardCooldown.durationMs(true, 0));
        assertEquals(0L, FactionCardCooldown.remainingMs(1L, 2L, false, 45));
        assertEquals(0L, FactionCardCooldown.remainingMs(1L, 2L, true, 0));
    }

    @Test
    void remainingUsesWallClockFromLastUse() {
        long used = 1_000_000L;
        long now = used + 10 * 60_000L;
        assertEquals(35 * 60_000L, FactionCardCooldown.remainingMs(used, now, true, 45));
        assertEquals(0L, FactionCardCooldown.remainingMs(used, used + 45 * 60_000L, true, 45));
        assertEquals(0L, FactionCardCooldown.remainingMs(0L, now, true, 45));
    }

    @Test
    void differentCardTypesTrackIndependentTimestamps() {
        BackpackState state = BackpackState.createDefault();
        state.markUsed(FactionCardType.KILLER, 1_000_000L);
        assertEquals(1_000_000L, state.lastUsedAt(FactionCardType.KILLER));
        assertEquals(0L, state.lastUsedAt(FactionCardType.CIVILIAN));
        long now = 1_000_000L + 60_000L;
        assertTrue(FactionCardCooldown.remainingMs(state.lastUsedAt(FactionCardType.KILLER), now, true, 45) > 0L);
        assertEquals(0L, FactionCardCooldown.remainingMs(state.lastUsedAt(FactionCardType.CIVILIAN), now, true, 45));
    }

    @Test
    void splitRoundsUpToAtLeastOneSecond() {
        assertEquals(new FactionCardCooldown.Remaining(12L, 30L),
                FactionCardCooldown.split(12 * 60_000L + 30_000L));
        assertEquals(new FactionCardCooldown.Remaining(0L, 1L), FactionCardCooldown.split(1L));
        assertEquals(new FactionCardCooldown.Remaining(1L, 0L), FactionCardCooldown.split(60_000L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalizedCoercesGsonNumericTimestamps() {
        BackpackState state = BackpackState.createDefault();
        Map raw = new HashMap<>();
        raw.put("KILLER", 1234567890.0);
        raw.put("CIVILIAN", 42);
        state.cardLastUsedAt = raw;
        state.normalized();
        assertEquals(1234567890L, state.lastUsedAt(FactionCardType.KILLER));
        assertEquals(42L, state.lastUsedAt(FactionCardType.CIVILIAN));
        assertEquals(0L, state.lastUsedAt(FactionCardType.NEUTRAL));
    }
}
