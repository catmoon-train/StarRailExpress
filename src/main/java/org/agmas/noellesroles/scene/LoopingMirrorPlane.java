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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 循环镜子的一个传送平面：由两个对角框选后压成最薄轴上的一层。
 *
 * <p>UV 约定与装饰镜子一致：X 面用 (Y, Z)，Y 面用 (X, Z)，Z 面用 (X, Y)。
 * {@code outward} 指向平面「后面」（客户端生成场景的一侧）；内侧 n &lt; 0，外侧 n &gt; 0。
 */
public final class LoopingMirrorPlane {
    public static final int MAX_SPAN = 64;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Direction outward;

    public LoopingMirrorPlane(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Direction outward) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.outward = outward;
    }

    public static LoopingMirrorPlane fromCorners(BlockPos a, BlockPos b, Direction hint) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int sx = maxX - minX;
        int sy = maxY - minY;
        int sz = maxZ - minZ;
        int minSpan = Math.min(sx, Math.min(sy, sz));
        Direction.Axis axis = hint.getAxis();
        int hintSpan = axis.choose(sx, sy, sz);
        if (hintSpan != minSpan) {
            if (sx <= sy && sx <= sz) {
                axis = Direction.Axis.X;
            } else if (sz <= sy) {
                axis = Direction.Axis.Z;
            } else {
                axis = Direction.Axis.Y;
            }
        }
        int layer = axis.choose(b.getX(), b.getY(), b.getZ());
        if (axis == Direction.Axis.X) {
            minX = maxX = layer;
        } else if (axis == Direction.Axis.Y) {
            minY = maxY = layer;
        } else {
            minZ = maxZ = layer;
        }
        Direction outward = hint.getAxis() == axis
                ? hint
                : Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return new LoopingMirrorPlane(minX, minY, minZ, maxX, maxY, maxZ, outward);
    }

    public LoopingMirrorPlane withOutward(Direction outward) {
        if (outward.getAxis() != axis()) {
            outward = Direction.fromAxisAndDirection(axis(), outward.getAxisDirection());
        }
        return new LoopingMirrorPlane(minX, minY, minZ, maxX, maxY, maxZ, outward);
    }

    public Direction outward() {
        return outward;
    }

    public Direction.Axis axis() {
        return outward.getAxis();
    }

    public boolean isValid() {
        return uSize() >= 1 && vSize() >= 1 && uSize() <= MAX_SPAN && vSize() <= MAX_SPAN;
    }

    public int uMin() {
        return axis() == Direction.Axis.X ? minY : minX;
    }

    public int uMax() {
        return axis() == Direction.Axis.X ? maxY : maxX;
    }

    public int vMin() {
        return axis() == Direction.Axis.X ? minZ : (axis() == Direction.Axis.Y ? minZ : minY);
    }

    public int vMax() {
        return axis() == Direction.Axis.X ? maxZ : (axis() == Direction.Axis.Y ? maxZ : maxY);
    }

    public int uSize() {
        return uMax() - uMin() + 1;
    }

    public int vSize() {
        return vMax() - vMin() + 1;
    }

    public int minAlong() {
        return axis().choose(minX, minY, minZ);
    }

    public int maxAlong() {
        return axis().choose(maxX, maxY, maxZ);
    }

    public int outwardLayer() {
        return outward.getAxisDirection() == Direction.AxisDirection.POSITIVE ? maxAlong() : minAlong();
    }

    public int inwardLayer() {
        return outward.getAxisDirection() == Direction.AxisDirection.POSITIVE ? minAlong() : maxAlong();
    }

    public Vec3 center() {
        return new Vec3((minX + maxX + 1) * 0.5D, (minY + maxY + 1) * 0.5D, (minZ + maxZ + 1) * 0.5D);
    }

    public AABB box() {
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    public BlockPos at(int layer, int u, int v) {
        return switch (axis()) {
            case X -> new BlockPos(layer, u, v);
            case Y -> new BlockPos(u, layer, v);
            case Z -> new BlockPos(u, v, layer);
        };
    }

    public BlockPos behind(int k, int u, int v) {
        return at(outwardLayer(), u, v).relative(outward, k);
    }

    public BlockPos interior(int k, int u, int v) {
        return at(inwardLayer(), u, v).relative(outward.getOpposite(), k);
    }

    public double uCoord(Vec3 pos) {
        return axis() == Direction.Axis.X ? pos.y : pos.x;
    }

    public double vCoord(Vec3 pos) {
        return axis() == Direction.Axis.X ? pos.z : (axis() == Direction.Axis.Y ? pos.z : pos.y);
    }

    public double uFrac(Vec3 pos) {
        return (uCoord(pos) - uMin()) / (double) uSize();
    }

    public double vFrac(Vec3 pos) {
        return (vCoord(pos) - vMin()) / (double) vSize();
    }

    /**
     * 有符号深度：0 在外侧面，正值为后面，负值为前面/内侧。
     */
    public double n(Vec3 pos) {
        double c = axis().choose(pos.x, pos.y, pos.z);
        if (outward.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return c - (outwardLayer() + 1);
        }
        return outwardLayer() - c;
    }

    public Vec3 fromFrac(double uFrac, double vFrac, double n) {
        double u = uMin() + uFrac * uSize();
        double v = vMin() + vFrac * vSize();
        double along;
        if (outward.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            along = outwardLayer() + 1 + n;
        } else {
            along = outwardLayer() - n;
        }
        return switch (axis()) {
            case X -> new Vec3(along, u, v);
            case Y -> new Vec3(u, along, v);
            case Z -> new Vec3(u, v, along);
        };
    }

    public Vec3 nUnit() {
        return Vec3.atLowerCornerOf(outward.getNormal());
    }

    public Vec3 uUnit() {
        return switch (axis()) {
            case X -> new Vec3(0.0D, 1.0D, 0.0D);
            case Y, Z -> new Vec3(1.0D, 0.0D, 0.0D);
        };
    }

    public Vec3 vUnit() {
        return switch (axis()) {
            case X, Y -> new Vec3(0.0D, 0.0D, 1.0D);
            case Z -> new Vec3(0.0D, 1.0D, 0.0D);
        };
    }

    public boolean containsUV(Vec3 pos, double pad) {
        double u = uCoord(pos);
        double v = vCoord(pos);
        return u >= uMin() - pad && u <= uMax() + 1.0D + pad
                && v >= vMin() - pad && v <= vMax() + 1.0D + pad;
    }

    public boolean crossed(Vec3 prev, Vec3 curr) {
        double n0 = n(prev);
        double n1 = n(curr);
        if (n0 * n1 > 0.0D) {
            return false;
        }
        if (Math.max(Math.abs(n0), Math.abs(n1)) > 2.5D) {
            return false;
        }
        double t = Math.abs(n1 - n0) < 1.0E-6D ? 0.5D : n0 / (n0 - n1);
        t = Mth.clamp(t, 0.0D, 1.0D);
        Vec3 hit = prev.add(curr.subtract(prev).scale(t));
        return containsUV(hit, 1.0D);
    }

    public int mapU(int destU, LoopingMirrorPlane dest) {
        double f = (destU - dest.uMin() + 0.5D) / dest.uSize();
        return uMin() + Mth.clamp((int) Math.floor(f * uSize()), 0, uSize() - 1);
    }

    public int mapV(int destV, LoopingMirrorPlane dest) {
        double f = (destV - dest.vMin() + 0.5D) / dest.vSize();
        return vMin() + Mth.clamp((int) Math.floor(f * vSize()), 0, vSize() - 1);
    }

    public AABB interiorBox(int depth) {
        BlockPos a = interior(1, uMin(), vMin());
        BlockPos b = interior(Math.max(1, depth), uMax(), vMax());
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()) + 1;
        int y1 = Math.max(a.getY(), b.getY()) + 1;
        int z1 = Math.max(a.getZ(), b.getZ()) + 1;
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public AABB behindBox(int depth) {
        BlockPos a = behind(1, uMin(), vMin());
        BlockPos b = behind(Math.max(1, depth), uMax(), vMax());
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()) + 1;
        int y1 = Math.max(a.getY(), b.getY()) + 1;
        int z1 = Math.max(a.getZ(), b.getZ()) + 1;
        return new AABB(x0, y0, z0, x1, y1, z1);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinX", minX);
        tag.putInt("MinY", minY);
        tag.putInt("MinZ", minZ);
        tag.putInt("MaxX", maxX);
        tag.putInt("MaxY", maxY);
        tag.putInt("MaxZ", maxZ);
        tag.putString("Outward", outward.getSerializedName());
        return tag;
    }

    public static @Nullable LoopingMirrorPlane load(CompoundTag tag) {
        Direction outward = Direction.byName(tag.getString("Outward"));
        if (outward == null) {
            return null;
        }
        LoopingMirrorPlane plane = new LoopingMirrorPlane(
                tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ"),
                tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ"),
                outward);
        return plane.isValid() ? plane : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LoopingMirrorPlane other)) {
            return false;
        }
        return minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ
                && outward == other.outward;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ, outward);
    }
}
