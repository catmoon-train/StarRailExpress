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

package io.wifi.starrailexpress.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
        if (blockPos == null)
            return tag;
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
