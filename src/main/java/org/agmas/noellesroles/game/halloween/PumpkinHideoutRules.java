package org.agmas.noellesroles.game.halloween;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 南瓜据点服务端判定和客户端边界共用的尺寸，避免显示范围与实际退出范围不同。 */
public final class PumpkinHideoutRules {
    public static final ResourceLocation SCALE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_hideout_scale");
    public static final double SCALE = 0.06;
    public static final double SPEED_MULTIPLIER = 0.05;
    public static final int LIFETIME_TICKS = 10 * 20;
    private static final double HALF_WIDTH = 0.8;
    private static final double BELOW = 0.35;
    private static final double ABOVE = 1.05;

    private PumpkinHideoutRules() {}

    /** 到期 tick 即失效，交互和世界清理使用同一边界。 */
    public static boolean expired(long now, long expiresAt) { return now >= expiresAt; }

    public static AABB bounds(Vec3 feet) {
        return new AABB(feet.x - HALF_WIDTH, feet.y - BELOW, feet.z - HALF_WIDTH,
                feet.x + HALF_WIDTH, feet.y + ABOVE, feet.z + HALF_WIDTH);
    }

    public static boolean contains(Vec3 feet, Vec3 position) {
        return Math.abs(position.x - feet.x) <= HALF_WIDTH && Math.abs(position.z - feet.z) <= HALF_WIDTH
                && position.y >= feet.y - BELOW && position.y <= feet.y + ABOVE;
    }
}
