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

package org.agmas.noellesroles.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BlindnessVisionVisibilityTest {
    @Test
    void targetShaderRevealsSurfaceInsideSoundRange() {
        assertTrue(targetFade(0.35f, 10.0f, 1.0f, 0.0f) > 0.5f);
    }

    @Test
    void targetShaderKeepsSurfaceOutsideSoundRangeBlack() {
        assertTrue(targetFade(1.1f, 10.0f, 1.0f, 0.0f) < 0.01f);
    }

    @Test
    void olderSourcesFadeOutThroughTheirAgeValue() {
        assertTrue(targetFade(0.7f, 10.0f, 1.0f, 1.0f) < targetFade(0.7f, 10.0f, 1.0f, 0.0f));
    }

    private static float targetFade(float normalizedDistance, float range, float volume, float age) {
        if (normalizedDistance > 1.0f) {
            return 0.0f;
        }
        float fade = normalizedDistance;
        float agedFade = clamp(1.0f - ((1.0f - fade * fade) - age) * Math.min(0.75f, volume), 0.0f, 1.0f);
        return clamp(1.0f - agedFade - 0.01f, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(max, Math.max(min, value));
    }
}
