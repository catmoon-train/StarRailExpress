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

package org.agmas.noellesroles.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 上下循环：勾选 A、B 两个对角后，这一段竖向空间按高度循环。
 *
 * <p>穿过顶面或底面会按同一 XZ 传送到另一端；客户端用 setBlock 在上方叠有限份，下方用雾收口。
 */
public final class VerticalLoopingMirrorLoop {
    public static final int MAX_SIZE_XZ = 24;
    public static final int MAX_SIZE_Y = 32;
    public static final int MIN_SIZE_Y = 2;
    public static final int MAX_UP_COPIES = 2;
    public static final int MAX_COPY_BLOCKS = 4096;
    public static final int MAX_ENTITY_SOURCES = 16;
    public static final int FOG_DEPTH = 10;

    private final BlockPos controller;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int copiesUp;

    public VerticalLoopingMirrorLoop(BlockPos controller, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        this.controller = controller.immutable();
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        int cells = sizeX() * sizeY() * sizeZ();
        this.copiesUp = cells > 6144 || sizeY() > 20 ? 1 : MAX_UP_COPIES;
    }

    public static @Nullable VerticalLoopingMirrorLoop create(BlockPos controller, BlockPos a, BlockPos b) {
        VerticalLoopingMirrorLoop loop = new VerticalLoopingMirrorLoop(
                controller, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        return loop.isValid() ? loop : null;
    }

    public boolean isValid() {
        return sizeX() >= 1 && sizeZ() >= 1 && sizeY() >= MIN_SIZE_Y
                && sizeX() <= MAX_SIZE_XZ && sizeZ() <= MAX_SIZE_XZ && sizeY() <= MAX_SIZE_Y;
    }

    public BlockPos controller() {
        return controller;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public int periodY() {
        return sizeY();
    }

    public int copiesUp() {
        return copiesUp;
    }

    public VerticalLoopingMirrorLoop withController(BlockPos pos) {
        return new VerticalLoopingMirrorLoop(pos, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean contains(BlockPos pos) {
        return controller.equals(pos);
    }

    public AABB cellBox() {
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    public AABB fogBox() {
        return new AABB(minX, minY - FOG_DEPTH, minZ, maxX + 1.0D, minY, maxZ + 1.0D);
    }

    public AABB searchBox() {
        return cellBox().inflate(1.0D, 2.0D, 1.0D);
    }

    public boolean containsXZ(Vec3 pos, double pad) {
        return pos.x >= minX - pad && pos.x <= maxX + 1.0D + pad
                && pos.z >= minZ - pad && pos.z <= maxZ + 1.0D + pad;
    }

    /**
     * @return +1 向上穿过顶面，-1 向下穿过底面，0 未穿过
     */
    public int crossed(Vec3 prev, Vec3 curr) {
        int top = crossedPlane(prev, curr, maxY + 1.0D);
        int bottom = crossedPlane(prev, curr, minY);
        if (top != 0 && bottom == 0) {
            return top;
        }
        if (bottom != 0 && top == 0) {
            return bottom;
        }
        if (top != 0) {
            return Math.abs(curr.y - (maxY + 1.0D)) <= Math.abs(curr.y - minY) ? top : bottom;
        }
        return 0;
    }

    private int crossedPlane(Vec3 prev, Vec3 curr, double planeY) {
        double n0 = prev.y - planeY;
        double n1 = curr.y - planeY;
        if (n0 * n1 > 0.0D) {
            return 0;
        }
        if (Math.max(Math.abs(n0), Math.abs(n1)) > 2.5D) {
            return 0;
        }
        double t = Math.abs(n1 - n0) < 1.0E-6D ? 0.5D : n0 / (n0 - n1);
        t = Mth.clamp(t, 0.0D, 1.0D);
        Vec3 hit = prev.add(curr.subtract(prev).scale(t));
        if (!containsXZ(hit, 1.0D)) {
            return 0;
        }
        return n1 >= n0 ? 1 : -1;
    }

    public Vec3 wrap(Vec3 pos, int direction) {
        return pos.add(0.0D, -direction * periodY(), 0.0D);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.putInt("MinX", minX);
        tag.putInt("MinY", minY);
        tag.putInt("MinZ", minZ);
        tag.putInt("MaxX", maxX);
        tag.putInt("MaxY", maxY);
        tag.putInt("MaxZ", maxZ);
        return tag;
    }

    public static @Nullable VerticalLoopingMirrorLoop load(CompoundTag tag) {
        BlockPos controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
        if (controller == null) {
            return null;
        }
        VerticalLoopingMirrorLoop loop = new VerticalLoopingMirrorLoop(
                controller,
                tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ"),
                tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ"));
        return loop.isValid() ? loop : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VerticalLoopingMirrorLoop other)) {
            return false;
        }
        return controller.equals(other.controller)
                && minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(controller, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
