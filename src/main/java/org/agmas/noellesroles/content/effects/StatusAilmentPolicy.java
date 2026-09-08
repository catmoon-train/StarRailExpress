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

/**
 * Timing and intensity formulas for the status-ailment potion set.
 * Amplifier 0 is level I. Higher amplifiers make harmful episodes more frequent
 * or stronger, matching the in-game potion level.
 */
public final class StatusAilmentPolicy {
    public static final int MISSING_LUNGS_BASE_SECONDS = 120;
    public static final int MISSING_LUNGS_SECONDS_PER_LEVEL = 5;
    public static final int MISSING_LUNGS_MIN_SECONDS = 5;

    public static final int APHRENIA_MIN_INTERVAL_SECONDS = 30;
    public static final int APHRENIA_MAX_INTERVAL_SECONDS = 45;
    public static final int APHRENIA_MIN_DURATION_SECONDS = 10;
    public static final int APHRENIA_MAX_DURATION_SECONDS = 15;

    public static final int INTELLECT_MIN_INTERVAL_SECONDS = 16;
    public static final int INTELLECT_MAX_INTERVAL_SECONDS = 24;
    public static final int INTELLECT_MIN_DURATION_SECONDS = 3;
    public static final int INTELLECT_MAX_DURATION_SECONDS = 5;

    private StatusAilmentPolicy() {
    }

    /** Seconds until 肺部缺失 kills. Level I = 115s (120 - 5). */
    public static int missingLungsSeconds(int amplifier) {
        int level = Math.max(0, amplifier) + 1;
        return Math.max(MISSING_LUNGS_MIN_SECONDS,
                MISSING_LUNGS_BASE_SECONDS - MISSING_LUNGS_SECONDS_PER_LEVEL * level);
    }

    public static int missingLungsTicks(int amplifier) {
        return missingLungsSeconds(amplifier) * 20;
    }

    public static int aphreniaMinIntervalTicks(int amplifier) {
        return Math.max(10 * 20, (APHRENIA_MIN_INTERVAL_SECONDS - amplifier * 4) * 20);
    }

    public static int aphreniaMaxIntervalTicks(int amplifier) {
        return Math.max(aphreniaMinIntervalTicks(amplifier) + 20,
                (APHRENIA_MAX_INTERVAL_SECONDS - amplifier * 5) * 20);
    }

    public static int aphreniaMinDurationTicks(int amplifier) {
        return (APHRENIA_MIN_DURATION_SECONDS + amplifier * 2) * 20;
    }

    public static int aphreniaMaxDurationTicks(int amplifier) {
        return Math.max(aphreniaMinDurationTicks(amplifier) + 20,
                (APHRENIA_MAX_DURATION_SECONDS + amplifier * 3) * 20);
    }

    public static int intellectMinIntervalTicks(int amplifier) {
        return Math.max(6 * 20, (INTELLECT_MIN_INTERVAL_SECONDS - amplifier * 2) * 20);
    }

    public static int intellectMaxIntervalTicks(int amplifier) {
        return Math.max(intellectMinIntervalTicks(amplifier) + 20,
                (INTELLECT_MAX_INTERVAL_SECONDS - amplifier * 2) * 20);
    }

    public static int intellectMinDurationTicks(int amplifier) {
        return (INTELLECT_MIN_DURATION_SECONDS + amplifier) * 20;
    }

    public static int intellectMaxDurationTicks(int amplifier) {
        return Math.max(intellectMinDurationTicks(amplifier) + 20,
                (INTELLECT_MAX_DURATION_SECONDS + amplifier * 2) * 20);
    }

    /** Chance per moving tick to trip. Level I ≈ 0.4%. */
    public static float motorFallChance(int amplifier) {
        return 0.004f + Math.max(0, amplifier) * 0.0025f;
    }

    public static int motorFallDurationTicks(int amplifier) {
        return 24 + Math.max(0, amplifier) * 8;
    }

    public static int motorFallCooldownTicks(int amplifier) {
        return Math.max(40, 90 - Math.max(0, amplifier) * 12);
    }

    public static float myopiaStrength(int amplifier) {
        float value = 0.38f + Math.max(0, amplifier) * 0.18f;
        return Math.min(1.0f, Math.max(0.2f, value));
    }

    /** Distance in blocks that stays relatively sharp. Higher levels focus closer. */
    public static float myopiaFocusBlocks(int amplifier) {
        return Math.max(1.25f, 6.0f - Math.max(0, amplifier) * 1.15f);
    }

    public static float handTremorDrawAmplitude(int amplifier) {
        return 1.15f + Math.max(0, amplifier) * 0.55f;
    }

    public static float handTremorIdleAmplitude(int amplifier) {
        return 0.16f + Math.max(0, amplifier) * 0.08f;
    }

    public static int pickRange(int minInclusive, int maxInclusive, int randomBits) {
        int min = Math.min(minInclusive, maxInclusive);
        int max = Math.max(minInclusive, maxInclusive);
        int span = max - min;
        if (span <= 0) {
            return min;
        }
        return min + Math.floorMod(randomBits, span + 1);
    }
}
