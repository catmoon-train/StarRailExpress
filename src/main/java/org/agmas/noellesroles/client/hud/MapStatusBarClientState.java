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

    public static int value() {
        return value;
    }

    public static int maxValue() {
        return maxValue;
    }
}
