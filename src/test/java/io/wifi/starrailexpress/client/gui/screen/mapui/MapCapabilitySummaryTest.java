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

import io.wifi.starrailexpress.network.MapDisplayInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 地图能力摘要现在直接读服务端解析好的 {@link MapDisplayInfo}（不再解析 JSON），
 * 这里验证「游泳派生」「字段透传」以及 null 兜底。
 */
class MapCapabilitySummaryTest {

    @Test
    void derivesSwimmingFromAreasSettingsCombination() {
        // 不能游但能跳，其它水下条件都满足 → 算可以下水
        var byJump = map("clear", false, false, false, 8, true, true, true, true, false);
        MapCapabilitySummary summary = MapCapabilitySummary.fromDisplayInfo(byJump);
        assertTrue(summary.canJump());
        assertTrue(summary.canSwim());

        // 允许进入深水为假 → 不能游泳
        var noDeepWater = map("clear", false, false, false, 8, true, true, true, false, true);
        assertFalse(MapCapabilitySummary.fromDisplayInfo(noDeepWater).canSwim());

        // 既不能跳也不能游 → 不能下水
        var neither = map("clear", false, false, false, 8, false, false, true, true, false);
        assertFalse(MapCapabilitySummary.fromDisplayInfo(neither).canSwim());
    }

    @Test
    void passesThroughEnvironmentFields() {
        var info = map("thunder", true, true, true, 8, false, true, true, true, false);
        MapCapabilitySummary summary = MapCapabilitySummary.fromDisplayInfo(info);

        assertFalse(summary.canJump());
        assertEquals("thunder", summary.weather());
        assertTrue(summary.snow());
        assertTrue(summary.sand());
        assertTrue(summary.oxygenDrowning());
        assertTrue(summary.minigameQuest());
        assertTrue(summary.planeCrash());
        assertEquals(8, summary.roomCount());
    }

    @Test
    void safelyFallsBackWhenMissing() {
        MapCapabilitySummary empty = MapCapabilitySummary.fromDisplayInfo(null);

        assertFalse(empty.canSwim());
        assertFalse(empty.canJump());
        assertEquals("clear", empty.weather());
        assertEquals(-1, empty.roomCount());
    }

    /**
     * 测试用紧凑构造：参数含义依次为
     * weather, winter(氧气/雪/沙三个开关一起), minigameQuest, planeCrash, roomCount,
     * canJump, canSimpleSwim, canUnderWater, allowInDeepWater, canSwim。
     */
    private static MapDisplayInfo map(String weather, boolean winter, boolean minigameQuest, boolean planeCrash,
            int roomCount, boolean canJump, boolean canSimpleSwim, boolean canUnderWater, boolean allowInDeepWater,
            boolean canSwim) {
        return new MapDisplayInfo(
                "areas1", // id
                "", // displayName
                "", // description
                0, // color
                List.of("UNDERWATER"), // features
                true, // hasVoteConfig
                -1, -1, true, List.of(), // minCount, maxCount, canSelect, gameModes
                roomCount, List.of(), List.of(), List.of(), // roomCount, disabledTasks/Roles/SceneTask
                minigameQuest, // minigameQuestEnabled
                false, false, false, "NONE", // 会议三开关 + mapStatusBar
                canJump, canSimpleSwim, canUnderWater, allowInDeepWater, canSwim,
                winter, // enableOxygenDrowning
                winter, // snowEnabled
                winter, // sandEnabled
                planeCrash, // planeCrashEventEnabled
                true, 100.0f, weather, // fogEnabled, fogEnd, weather
                0.0D, 18000L, false, false, // gravityModifier, time, daylightCycle, weatherCycle
                List.of(), List.of()); // mobEffects, initialItems
    }
}
