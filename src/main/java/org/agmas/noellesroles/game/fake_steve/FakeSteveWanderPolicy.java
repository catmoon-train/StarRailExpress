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

package org.agmas.noellesroles.game.fake_steve;

/** Destination rules for idle wandering so the body does not pace one tile. */
public final class FakeSteveWanderPolicy {
    public static final double MIN_TASK_DISTANCE_SQR = 64.0D;
    public static final double SOCIAL_MIN_SQR = 36.0D;
    public static final double SOCIAL_MAX_SQR = 100.0D;
    public static final double AVOID_LAST_DISTANCE = 6.0D;
    public static final double AVOID_PLATE_DISTANCE = 4.0D;

    private FakeSteveWanderPolicy() {
    }

    public static boolean isUsableTaskPoint(double distanceSqr, boolean nearLastWander,
            boolean recentPlate) {
        return !recentPlate && !nearLastWander && distanceSqr >= MIN_TASK_DISTANCE_SQR;
    }

    public static boolean isSocialStand(double distanceToPlayerSqr) {
        return distanceToPlayerSqr >= SOCIAL_MIN_SQR && distanceToPlayerSqr <= SOCIAL_MAX_SQR;
    }

    public static boolean shouldReselectNow(boolean noGoal, int pathFailures, boolean decisionDue) {
        return noGoal || pathFailures >= 4 || decisionDue;
    }
}
