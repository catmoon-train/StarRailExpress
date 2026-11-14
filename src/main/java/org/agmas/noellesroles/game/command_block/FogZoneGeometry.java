package org.agmas.noellesroles.game.command_block;

/** 雾区的圆柱边界，服务端判定与客户端遮挡共用。 */
public final class FogZoneGeometry {
    public static final double RADIUS = 15.0D;
    public static final double HEIGHT = 5.0D;
    private FogZoneGeometry() {}

    public static boolean contains(double relativeX, double relativeY, double relativeZ) {
        return relativeY >= 0 && relativeY <= HEIGHT
                && relativeX * relativeX + relativeZ * relativeZ <= RADIUS * RADIUS;
    }
}
