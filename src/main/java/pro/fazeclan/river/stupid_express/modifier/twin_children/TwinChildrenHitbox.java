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
     * Stacked collision height after half-scale. Tall enough to block 1-block
     * gaps, short enough that the upper twin's knife/gun is not inside the
     * lower twin's hitbox.
     */
    public static final float STACKED_COLLISION_HEIGHT = 1.1F;

    /** Half-scale factor applied by the Twin Children attribute modifier. */
    public static final float HALF_SCALE_FACTOR = 0.5F;

    /** Unscaled collision height so that {@code height * 0.5 = 1.1}. */
    public static final float STACKED_UNSCALED_HEIGHT = STACKED_COLLISION_HEIGHT / HALF_SCALE_FACTOR;

    /**
     * Upper twin collision height after half-scale. Shorter than the visual
     * 0.9 model so the rider is not wrapped by a tall hitbox.
     */
    public static final float UPPER_COLLISION_HEIGHT = 0.7F;

    /** Unscaled collision height so that {@code height * 0.5 = 0.7}. */
    public static final float UPPER_UNSCALED_HEIGHT = UPPER_COLLISION_HEIGHT / HALF_SCALE_FACTOR;

    private TwinChildrenHitbox() {
    }

    /**
     * Height multiplier applied to unscaled pose dimensions so the stacked
     * lower twin's collision is {@link #STACKED_UNSCALED_HEIGHT}.
     */
    public static float stackedHeightScale(float currentUnscaledHeight) {
        return heightScaleTo(currentUnscaledHeight, STACKED_UNSCALED_HEIGHT);
    }

    /**
     * Height multiplier so the stacked upper twin's collision is
     * {@link #UPPER_UNSCALED_HEIGHT}.
     */
    public static float upperHeightScale(float currentUnscaledHeight) {
        return heightScaleTo(currentUnscaledHeight, UPPER_UNSCALED_HEIGHT);
    }

    static float heightScaleTo(float currentUnscaledHeight, float targetUnscaledHeight) {
        if (currentUnscaledHeight <= 0.01F) {
            return 1.0F;
        }
        return targetUnscaledHeight / currentUnscaledHeight;
    }

    /**
     * Passenger attachment Y so that after subtracting the rider's vehicle
     * attachment, their feet sit on the visual head ({@code 1.8 * vehicleScale}).
     */
    public static double headPassengerAttachmentY(float vehicleScale, double passengerVehicleAttachY) {
        return VISUAL_STANDING_HEIGHT * vehicleScale + passengerVehicleAttachY;
    }
}
