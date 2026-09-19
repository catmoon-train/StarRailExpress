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
     * World-space height of the lower twin's hitbox at {@link #HALF_SCALE_FACTOR}.
     *
     * <p>
     * Deliberately taller than the lower twin's half-scale model (0.9) so the unit
     * cannot crawl through 1-block gaps. Rendering is left untouched.
     */
    public static final float STACKED_COLLISION_HEIGHT = 1.3F;

    /** Half-scale factor applied by the Twin Children attribute modifier. */
    public static final float HALF_SCALE_FACTOR = 0.5F;

    /** Unscaled collision height so that {@code height * 0.5 = 1.3}. */
    public static final float STACKED_UNSCALED_HEIGHT = 2.6F;

    /**
     * Upper twin collision height after half-scale (0.5 world).
     *
     * <p>
     * The rider is attached so its collision box starts at the lower twin's
     * collision top ({@code STACKED_UNSCALED_HEIGHT * scale = 1.3}, see
     * {@link #stackedPassengerAttachmentY}), giving a {@code 1.3..1.8} box: the
     * whole stack is exactly {@code 1.3 + 0.5 = 1.8} tall with zero overlap. The
     * rider's model keeps its natural half-scale size (drawn at {@code 1.3..2.2}).
     */
    public static final float UPPER_COLLISION_HEIGHT = 0.5F;

    /** Unscaled collision height so that {@code height * 0.5 = 0.5}. */
    public static final float UPPER_UNSCALED_HEIGHT = 1.0F;

    /**
     * Collision width kept by both twins after half-scale (0.3 world). The width
     * is intentionally not trimmed beyond the attribute scale so neither twin
     * ends up with a slimmer box than the other.
     */
    public static final float STACKED_COLLISION_WIDTH = 0.6F;

    /** Unscaled collision width so that {@code width * 0.5 = 0.6}. */
    private static final float STACKED_UNSCALED_WIDTH = 1.2F;

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

    /**
     * Width multiplier so a stacked twin keeps its {@link #STACKED_COLLISION_WIDTH}
     * (0.3 world) box; only the height is ever retuned for stacking.
     */
    public static float stackedWidthScale(float currentUnscaledWidth) {
        return heightScaleTo(currentUnscaledWidth, STACKED_UNSCALED_WIDTH);
    }

    static float heightScaleTo(float currentUnscaledHeight, float targetUnscaledHeight) {
        if (currentUnscaledHeight <= 0.01F) {
            return 1.0F;
        }
        return targetUnscaledHeight / currentUnscaledHeight;
    }

    /**
     * Passenger attachment Y that puts the rider's feet - and therefore its
     * collision box - on the lower twin's collision top.
     *
     * <p>
     * {@code Entity#positionRider} ends up at
     * {@code vehicleY + attachY - rider.getVehicleAttachmentPoint(vehicle).y},
     * so the rider's own attachment offset has to be added back to land on
     * {@code STACKED_UNSCALED_HEIGHT * scale = 1.3}. Nothing but
     * {@code positionRider} reads this value, so the rider's collision is exactly
     * {@code 1.3..1.8} and cannot overlap the lower twin's {@code 0..1.3} box.
     */
    public static double stackedPassengerAttachmentY(float vehicleScale, double passengerVehicleAttachY) {
        return STACKED_UNSCALED_HEIGHT * vehicleScale + passengerVehicleAttachY;
    }

    /**
     * Extra scale applied to the <em>lower</em> twin's rendered model.
     *
     * <p>
     * The SCALE attribute only halves the model, so the lower twin would be drawn
     * 0.9 tall while its gap-blocking collision box is 1.3. Scaling the render
     * matrix by {@code STACKED_UNSCALED_HEIGHT / VISUAL_STANDING_HEIGHT = 1.444...}
     * stretches that model to exactly {@link #STACKED_COLLISION_HEIGHT} without
     * touching the attribute, physics, collision or any other SCALE consumer.
     */
    public static float lowerModelScaleFactor() {
        return STACKED_UNSCALED_HEIGHT / VISUAL_STANDING_HEIGHT;
    }

    /**
     * Extra scale applied to the <em>upper</em> twin's rendered model.
     *
     * <p>
     * The upper twin's entity - and therefore its model - already sits on the
     * lower twin's collision top ({@code 1.3}) with no extra drop, and its
     * collision box spans {@code 1.3..1.8}, i.e. 0.5 world. A whole model would be
     * drawn 0.9 tall and stick out above the pair, so it is scaled to
     * {@code UPPER_COLLISION_HEIGHT / (VISUAL_STANDING_HEIGHT * HALF_SCALE_FACTOR)
     * = 0.5 / 0.9 = 0.5556} to fill exactly that box. The collision width is not
     * tied to it and stays {@link #STACKED_COLLISION_WIDTH}.
     */
    public static float upperModelScaleFactor() {
        float naturalUpperHeight = VISUAL_STANDING_HEIGHT * HALF_SCALE_FACTOR;
        return UPPER_COLLISION_HEIGHT / naturalUpperHeight;
    }

    /** World Y of the invisible seat sitting on the lower twin's visual head. */
    public static double headSeatY(double lowerY, float lowerScale) {
        return lowerY + VISUAL_STANDING_HEIGHT * lowerScale;
    }

    /** Rider offset on the invisible seat. */
    public static final double SEAT_PASSENGER_ATTACHMENT_Y = 0.05D;
}
