/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

/** Minecraft-free helpers for Twin Children vehicle / seat stacking. */
final class TwinChildrenRideLogic {
    private TwinChildrenRideLogic() {
    }

    /**
     * The lower twin may stay on chairs, wheelchairs, and other non-player
     * vehicles. Riding another player would invert or break the stack.
     */
    static boolean keepLowerOnVehicle(boolean vehicleIsPlayer) {
        return !vehicleIsPlayer;
    }

    /**
     * The upper twin already rides the lower. Mounting anything else should
     * move the whole stack instead of stealing the rider.
     */
    static boolean redirectMountToLower(boolean stackedUpper, boolean vehicleIsCurrentVehicle,
            boolean vehicleIsPlayer) {
        return stackedUpper && !vehicleIsCurrentVehicle && !vehicleIsPlayer;
    }
}
