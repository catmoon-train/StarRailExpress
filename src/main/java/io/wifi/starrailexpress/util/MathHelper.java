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

    /**
     * Copied From Java 25 Math
     */
    public static int powExact(int x, int n) {
        /* See the comment in unsignedPowExact(long,int) for the details. */
        if (n < 0) {
            throw new ArithmeticException("negative exponent");
        }
        if (n == 0) {
            return 1;
        }
        if (x == 0 || x == 1) {
            return x;
        }
        if (x == -1) {
            return (n & 0b1) == 0 ? 1 : -1;
        }

        int p = 1;
        while (n > 1) {
            if ((n & 0b1) != 0) {
                p *= x;
            }
            x = Math.multiplyExact(x, x);
            n >>>= 1;
        }
        return Math.multiplyExact(p, x);
    }

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