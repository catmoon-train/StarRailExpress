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

package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.game.data.MapStatusBarType;

public final class MapStatusBarClientState {
    private static MapStatusBarType type = MapStatusBarType.NONE;
    private static int value = 20;
    private static int maxValue = 20;

    private MapStatusBarClientState() {
    }

    public static void set(MapStatusBarType newType, int newValue, int newMaxValue) {
        type = newType == null ? MapStatusBarType.NONE : newType;
        value = Math.max(0, newValue);
        maxValue = Math.max(1, newMaxValue);
    }

    public static MapStatusBarType type() {
        return type;
    }

    /**
     * 实际生效的状态条类型：服务端同步来的类型为主，地图区域设置可以覆盖它。
     *
     * <p>
     * HUD 渲染与排版判断都走这里，确保「这根条到底会不会画出来」只有一处结论。
     */
    public static MapStatusBarType effectiveType() {
        if (type == MapStatusBarType.NONE) {
            return MapStatusBarType.NONE;
        }
        var areas = io.wifi.starrailexpress.client.SREClient.areaComponent;
        if (areas != null && areas.areasSettings != null && areas.areasSettings.mapStatusBar != null) {
            return areas.areasSettings.mapStatusBar;
        }
        return type;
    }

    public static int value() {
        return value;
    }

    public static int maxValue() {
        return maxValue;
    }
}
