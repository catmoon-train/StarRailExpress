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
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 一块循环镜子上的一套循环：两个框选平面互为出入口。
 *
 * <p>穿过任一平面的正面或背面，都会按 UV 映射到另一平面的对应位置，并旋转速度/朝向以匹配平面夹角。
 */
public final class LoopingMirrorLoop {
    public static final int MAX_COPY_DEPTH = 32;

    private final BlockPos controller;
    private final LoopingMirrorPlane planeA;
    private final LoopingMirrorPlane planeB;
    private final int copyDepth;

    public LoopingMirrorLoop(BlockPos controller, LoopingMirrorPlane planeA, LoopingMirrorPlane planeB) {
        this.controller = controller.immutable();
        this.planeA = planeA;
        this.planeB = planeB;
        this.copyDepth = Mth.clamp((int) Math.round(planeA.center().distanceTo(planeB.center())), 4, MAX_COPY_DEPTH);
    }

    public static @Nullable LoopingMirrorLoop create(BlockPos controller, LoopingMirrorPlane rawA,
            LoopingMirrorPlane rawB) {
        if (!rawA.isValid() || !rawB.isValid()) {
            return null;
        }
        LoopingMirrorPlane planeA = rawA.withOutward(resolveOutward(rawA, rawB));
        LoopingMirrorPlane planeB = rawB.withOutward(resolveOutward(rawB, rawA));
        if (planeA.box().intersects(planeB.box()) && planeA.axis() == planeB.axis()
                && planeA.minAlong() == planeB.minAlong()) {
            return null;
        }
        return new LoopingMirrorLoop(controller, planeA, planeB);
    }

    private static Direction resolveOutward(LoopingMirrorPlane self, LoopingMirrorPlane other) {
        Vec3 toSelf = self.center().subtract(other.center());
        Direction.Axis axis = self.axis();
        double along = axis.choose(toSelf.x, toSelf.y, toSelf.z);
        if (Math.abs(along) < 1.0E-4D) {
            return self.outward();
        }
        return Direction.fromAxisAndDirection(axis,
                along > 0.0D ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    public BlockPos controller() {
        return controller;
    }

    public LoopingMirrorPlane planeA() {
        return planeA;
    }

    public LoopingMirrorPlane planeB() {
        return planeB;
    }

    public int copyDepth() {
        return copyDepth;
    }

    public LoopingMirrorLoop withController(BlockPos pos) {
        return new LoopingMirrorLoop(pos, planeA, planeB);
    }

    public boolean isValid() {
        return planeA.isValid() && planeB.isValid();
    }

    public boolean contains(BlockPos pos) {
        return controller.equals(pos);
    }

    public LoopingMirrorPlane other(LoopingMirrorPlane plane) {
        return plane.equals(planeA) ? planeB : planeA;
    }

    public @Nullable LoopingMirrorPlane crossedFrom(Vec3 prev, Vec3 curr) {
        boolean a = planeA.crossed(prev, curr);
        boolean b = planeB.crossed(prev, curr);
        if (a && !b) {
            return planeA;
        }
        if (b && !a) {
            return planeB;
        }
        if (a) {
            return Math.abs(planeA.n(curr)) <= Math.abs(planeB.n(curr)) ? planeA : planeB;
        }
        return null;
    }

    public Vec3 mapPoint(LoopingMirrorPlane from, LoopingMirrorPlane to, Vec3 pos) {
        return to.fromFrac(from.uFrac(pos), from.vFrac(pos), -from.n(pos));
    }

    public Vec3 mapVec(LoopingMirrorPlane from, LoopingMirrorPlane to, Vec3 vec) {
        double du = vec.dot(from.uUnit()) * (to.uSize() / (double) from.uSize());
        double dv = vec.dot(from.vUnit()) * (to.vSize() / (double) from.vSize());
        double dn = vec.dot(from.nUnit());
        return to.uUnit().scale(du).add(to.vUnit().scale(dv)).add(to.nUnit().scale(-dn));
    }

    public float yawOf(Vec3 look) {
        return (float) (Mth.atan2(-look.x, look.z) * (180.0D / Math.PI));
    }

    public float pitchOf(Vec3 look) {
        double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
        return (float) (Mth.atan2(-look.y, horiz) * (180.0D / Math.PI));
    }

    public AABB searchBox() {
        return planeA.box().minmax(planeB.box())
                .minmax(planeA.behindBox(2))
                .minmax(planeB.behindBox(2))
                .inflate(2.0D);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.put("PlaneA", planeA.save());
        tag.put("PlaneB", planeB.save());
        return tag;
    }

    public static @Nullable LoopingMirrorLoop load(CompoundTag tag) {
        BlockPos controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
        if (controller == null || !tag.contains("PlaneA") || !tag.contains("PlaneB")) {
            return null;
        }
        LoopingMirrorPlane a = LoopingMirrorPlane.load(tag.getCompound("PlaneA"));
        LoopingMirrorPlane b = LoopingMirrorPlane.load(tag.getCompound("PlaneB"));
        if (a == null || b == null) {
            return null;
        }
        LoopingMirrorLoop loop = new LoopingMirrorLoop(controller, a, b);
        return loop.isValid() ? loop : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LoopingMirrorLoop other)) {
            return false;
        }
        return controller.equals(other.controller)
                && planeA.equals(other.planeA)
                && planeB.equals(other.planeB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(controller, planeA, planeB);
    }
}
