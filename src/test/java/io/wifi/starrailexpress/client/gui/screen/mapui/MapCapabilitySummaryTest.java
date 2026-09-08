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

package io.wifi.starrailexpress.client.gui.screen.mapui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class MapCapabilitySummaryTest {

    @Test
    void readsNestedSettingsAndDerivesSwimming() {
        var json = JsonParser.parseString("""
                {
                  "settings": {
                    "canJump": true,
                    "canSwim": false,
                    "canSimpleSwim": true,
                    "canUnderWater": true,
                    "allowInDeepWater": true,
                    "weather": "thunder",
                    "snowEnabled": true,
                    "enableOxygenDrowning": true
                  },
                  "minigameQuestEnabled": true,
                  "roomCount": 8
                }
                """).getAsJsonObject();

        MapCapabilitySummary summary = MapCapabilitySummary.fromJson(json);

        assertTrue(summary.canJump());
        assertTrue(summary.canSwim());
        assertEquals("thunder", summary.weather());
        assertTrue(summary.snow());
        assertTrue(summary.oxygenDrowning());
        assertTrue(summary.minigameQuest());
        assertEquals(8, summary.roomCount());
    }

    @Test
    void readsLegacyRootFields() {
        var json = JsonParser.parseString("""
                {
                  "canSwim": true,
                  "canJump": false,
                  "weather": "rain",
                  "sandEnabled": true
                }
                """).getAsJsonObject();

        MapCapabilitySummary summary = MapCapabilitySummary.fromJson(json);

        assertTrue(summary.canSwim());
        assertFalse(summary.canJump());
        assertEquals("rain", summary.weather());
        assertTrue(summary.sand());
        assertEquals(-1, summary.roomCount());
    }

    @Test
    void safelyFallsBackForMissingOrInvalidValues() {
        var json = JsonParser.parseString("""
                {
                  "settings": {
                    "canJump": {},
                    "canSwim": "not-a-boolean",
                    "weather": []
                  },
                  "roomCount": "unknown"
                }
                """).getAsJsonObject();

        MapCapabilitySummary summary = MapCapabilitySummary.fromJson(json);
        MapCapabilitySummary empty = MapCapabilitySummary.fromJson(null);

        assertFalse(summary.canJump());
        assertFalse(summary.canSwim());
        assertEquals("clear", summary.weather());
        assertEquals(-1, summary.roomCount());
        assertFalse(empty.canSwim());
        assertFalse(empty.canJump());
        assertEquals("clear", empty.weather());
    }
}
