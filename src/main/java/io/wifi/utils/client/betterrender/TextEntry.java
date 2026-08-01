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

package io.wifi.utils.client.betterrender;

import net.minecraft.network.chat.Component;

/**
 * Represents a single text draw call pending in the batch.
 */
public record TextEntry(
        Component text,
        float x,
        float y,
        int color,
        boolean shadow,
        long throttleKey,   // unique id for throttle cache; -1 = no throttle
        long intervalMs     // throttle interval; 0 = no throttle
) {
    /** Convenience constructor — no throttle, raw string */
    public static TextEntry of(String text, float x, float y, int color, boolean shadow) {
        return new TextEntry(Component.literal(text), x, y, color, shadow, -1, 0);
    }

    /** Convenience constructor — no throttle, Component */
    public static TextEntry of(Component text, float x, float y, int color, boolean shadow) {
        return new TextEntry(text, x, y, color, shadow, -1, 0);
    }
}