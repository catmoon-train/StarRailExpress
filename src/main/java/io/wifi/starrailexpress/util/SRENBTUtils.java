package io.wifi.starrailexpress.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

// Author: wifi_left
public class SRENBTUtils {
    public static CompoundTag vec3ToTag(Vec3 vec3) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", vec3.x);
        tag.putDouble("y", vec3.y);
        tag.putDouble("z", vec3.z);
        return tag;
    }

    public static @Nullable Vec3 tagToVec3(CompoundTag tag) {
        double x = 0;
        double y = 0;
        double z = 0;
        if (tag.contains("x", CompoundTag.TAG_DOUBLE)) {
            x = tag.getDouble("x");
        } else {
            return null;
        }
        if (tag.contains("y", CompoundTag.TAG_DOUBLE)) {
            y = tag.getDouble("y");
        } else {
            return null;
        }
        if (tag.contains("z", CompoundTag.TAG_DOUBLE)) {
            z = tag.getDouble("z");
        } else {
            return null;
        }
        return new Vec3(x, y, z);
    }

    public static CompoundTag blockPosToTag(BlockPos blockPos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", blockPos.getX());
        tag.putInt("y", blockPos.getY());
        tag.putInt("z", blockPos.getZ());
        return tag;
    }

    public static @Nullable BlockPos tagToBlockPos(CompoundTag tag) {
        int x = 0;
        int y = 0;
        int z = 0;
        if (tag.contains("x", CompoundTag.TAG_INT)) {
            x = tag.getInt("x");
        } else {
            return null;
        }
        if (tag.contains("y", CompoundTag.TAG_INT)) {
            y = tag.getInt("y");
        } else {
            return null;
        }
        if (tag.contains("z", CompoundTag.TAG_INT)) {
            z = tag.getInt("z");
        } else {
            return null;
        }
        return new BlockPos(x, y, z);
    }
}
