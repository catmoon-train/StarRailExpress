package io.wifi.starrailexpress.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class BlockUtils {

    public static Vec2 get2DHit(Vec3 hitPos, BlockPos blockPos, Direction side) {
        Vec3 pos = hitPos.subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        float x = (float) pos.x;
        float y = (float) pos.y;
        float z = (float) pos.z;
        return switch (side) {
            case NORTH -> new Vec2(1 - x, y);
            case EAST -> new Vec2(1 - z, y);
            case SOUTH -> new Vec2(x, y);
            case WEST -> new Vec2(z, y);
            case UP -> new Vec2(1 - x, z);
            case DOWN -> new Vec2(1 - x, 1 - z);
        };
    }

}
