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

import java.util.List;

final class FakeSteveCrowdAvoidance {
    private static final double LOOK_AHEAD = 2.4D;
    private static final double CORRIDOR_HALF_WIDTH = 0.9D;
    private static final double LANE_OFFSET = 0.85D;
    private static final double CLEARANCE_TIE_EPSILON = 0.12D;

    private FakeSteveCrowdAvoidance() {
    }

    static Decision decide(double actorX, double actorZ, double targetX, double targetZ,
                           List<NearbyPlayer> players, int crowdedTicks) {
        double dx = targetX - actorX;
        double dz = targetZ - actorZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D || players.isEmpty()) {
            return Decision.CLEAR;
        }
        double forwardX = dx / length;
        double forwardZ = dz / length;
        double leftX = forwardZ;
        double leftZ = -forwardX;

        NearbyPlayer blocker = null;
        double blockerForward = Double.MAX_VALUE;
        for (NearbyPlayer player : players) {
            double relativeX = player.x() - actorX;
            double relativeZ = player.z() - actorZ;
            double ahead = relativeX * forwardX + relativeZ * forwardZ;
            double side = relativeX * leftX + relativeZ * leftZ;
            if (ahead >= -0.15D && ahead <= LOOK_AHEAD
                    && Math.abs(side) <= CORRIDOR_HALF_WIDTH && ahead < blockerForward) {
                blocker = player;
                blockerForward = ahead;
            }
        }
        if (blocker == null) {
            return Decision.CLEAR;
        }

        double leftClearance = laneClearance(actorX, actorZ, forwardX, forwardZ,
                leftX, leftZ, 1.0D, players);
        double rightClearance = laneClearance(actorX, actorZ, forwardX, forwardZ,
                leftX, leftZ, -1.0D, players);
        double clearanceDifference = leftClearance - rightClearance;
        // Tiny player-position jitter must not flip the selected lane every
        // decision. Prefer one deterministic side while both lanes are nearly equal.
        float strafe = Math.abs(clearanceDifference) <= CLEARANCE_TIE_EPSILON
                ? 0.75F : clearanceDifference > 0.0D ? 0.75F : -0.75F;
        float forwardScale = blockerForward < 0.9D ? 0.0F : 0.35F;
        return new Decision(forwardScale, strafe, true, crowdedTicks >= 8);
    }

    private static double laneClearance(double actorX, double actorZ,
                                        double forwardX, double forwardZ,
                                        double leftX, double leftZ, double side,
                                        List<NearbyPlayer> players) {
        double laneX = actorX + forwardX * 1.1D + leftX * LANE_OFFSET * side;
        double laneZ = actorZ + forwardZ * 1.1D + leftZ * LANE_OFFSET * side;
        double closest = Double.MAX_VALUE;
        for (NearbyPlayer player : players) {
            double dx = player.x() - laneX;
            double dz = player.z() - laneZ;
            closest = Math.min(closest, dx * dx + dz * dz);
        }
        return closest;
    }

    record NearbyPlayer(double x, double z) {
    }

    record Decision(float forwardScale, float strafe, boolean crowded, boolean shouldRepath) {
        private static final Decision CLEAR = new Decision(1.0F, 0.0F, false, false);
    }
}
