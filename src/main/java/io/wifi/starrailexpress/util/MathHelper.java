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

package io.wifi.starrailexpress.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class MathHelper extends Mth {
    public static float clamp(float value, float min, float max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static float clampNorm(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    public static double clampNorm(double value) {
        return clamp(value, 0.0d, 1.0d);
    }

    public static Vec3 toRadians(Vec3 angle) {
        return new Vec3(Math.toRadians(angle.x), Math.toRadians(angle.y), Math.toRadians(angle.z));
    }
}