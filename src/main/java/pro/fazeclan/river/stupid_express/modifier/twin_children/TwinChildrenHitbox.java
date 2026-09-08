/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

/** Pure hitbox math for stacked Twin Children. Keep free of Minecraft types. */
public final class TwinChildrenHitbox {
    /** Unscaled standing height of a player model. */
    public static final float VISUAL_STANDING_HEIGHT = 1.8F;

    /**
     * Unscaled collision height of the stacked unit. After the half-scale
     * attribute this becomes 1.8, matching two half-size players.
     */
    public static final float STACKED_UNSCALED_HEIGHT = VISUAL_STANDING_HEIGHT * 2.0F;

    private TwinChildrenHitbox() {
    }

    /**
     * Height multiplier applied to unscaled pose dimensions so the stacked
     * lower twin's collision is {@link #STACKED_UNSCALED_HEIGHT}.
     */
    public static float stackedHeightScale(float currentUnscaledHeight) {
        if (currentUnscaledHeight <= 0.01F) {
            return 1.0F;
        }
        return Math.max(1.0F, STACKED_UNSCALED_HEIGHT / currentUnscaledHeight);
    }

    /**
     * Passenger attachment Y so that after subtracting the rider's vehicle
     * attachment, their feet sit on the visual head ({@code 1.8 * vehicleScale}).
     */
    public static double headPassengerAttachmentY(float vehicleScale, double passengerVehicleAttachY) {
        return VISUAL_STANDING_HEIGHT * vehicleScale + passengerVehicleAttachY;
    }
}
