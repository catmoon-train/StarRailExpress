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
package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.entity.LockEntityManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 玩家尺寸的三维导航：楼梯、落差、门、梯子和有方向的水电梯共用一张图。 */
final class FakeSteveNavigator {
    private static final int MAX_VISITED = 4096;
    private static final double HALF_WIDTH = 0.305D;
    private static final double HEIGHT = 1.8D;
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1, -2, -3};
    /** 旧网格适配器共用的水平邻接顺序，保留路线判定的确定性。 */
    static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private FakeSteveNavigator() {
    }

    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal) {
        return find(level, mover, goal, false);
    }

    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal,
            boolean explicitTarget) {
        SearchWorld world = new SearchWorld(level, mover);
        BlockPos start = normalize(level, mover.blockPosition());
        BlockPos destination = normalize(level, goal);
        var result = FakeStevePathSearch.find(start, destination::equals,
                pos -> heuristic(pos, destination), world::neighbours,
                explicitTarget ? MAX_VISITED * 2 : MAX_VISITED);
        return new ArrayDeque<>(result.path());
    }

    static boolean reaches(ArrayDeque<BlockPos> path, BlockPos goal) {
        BlockPos last = path.peekLast();
        return last != null && last.equals(goal);
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dz = Math.abs(a.getZ() - b.getZ());
        // 一次水平移动最多下降三格，不能用曼哈顿高度高估剩余代价。
        return Math.max(Math.max(dx, dz), Math.abs(a.getY() - b.getY()) / 3.0D);
    }

    static BlockPos normalize(ServerLevel level, BlockPos pos) {
        for (int dy : new int[] {0, 1, -1, -2}) {
            BlockPos candidate = pos.offset(0, dy, 0);
            if (standable(level, candidate)) return candidate.immutable();
        }
        return pos.immutable();
    }

    static boolean safeStand(ServerLevel level, BlockPos pos) {
        return standable(level, normalize(level, pos));
    }

    static boolean isStepBlock(BlockState state) {
        return state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock;
    }

    static double standingY(ServerLevel level, BlockPos feet) {
        var state = level.getBlockState(feet);
        var shape = state.getCollisionShape(level, feet);
        if (!shape.isEmpty() && !FakeSteveDoorAccess.isOpenablePassage(state)) {
            double height = shape.max(Direction.Axis.Y);
            if (height <= 0.6D || isStepBlock(state)) return feet.getY() + height;
        }
        return feet.getY();
    }

    /** 旧网格适配器的站立表面高度，包含脚下半砖/楼梯及下层碰撞面。 */
    static double standingSurfaceY(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        VoxelShape feetShape = feetState.getCollisionShape(level, feet);
        if (!feetShape.isEmpty()) {
            double height = feetShape.max(Direction.Axis.Y);
            if (FakeStevePathPolicy.feetCanOccupy(false, height, isStepBlock(feetState),
                    feetState.getBlock() instanceof SlabBlock, false)) {
                return feet.getY() + height;
            }
        }
        BlockPos below = feet.below();
        VoxelShape belowShape = level.getBlockState(below).getCollisionShape(level, below);
        return belowShape.isEmpty() ? feet.getY() : below.getY() + belowShape.max(Direction.Axis.Y);
    }

    static Vec3 point(ServerLevel level, BlockPos feet) {
        return new Vec3(feet.getX() + 0.5D, standingY(level, feet), feet.getZ() + 0.5D);
    }

    static boolean standable(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet) || level.isOutsideBuildHeight(feet)
                || level.isOutsideBuildHeight(feet.above())) return false;
        BlockState state = level.getBlockState(feet);
        if (dangerous(level, feet) || dangerous(level, feet.below())) return false;
        double y = standingY(level, feet);
        boolean water = water(level, feet) || water(level, feet.above());
        if (water && !swimmingAllowed(level)) return false;
        boolean climbing = climbable(level, feet);
        var below = level.getBlockState(feet.below()).getCollisionShape(level, feet.below());
        var own = state.getCollisionShape(level, feet);
        boolean supported = !below.isEmpty() && below.max(Direction.Axis.Y) >= 0.45D
                || !own.isEmpty() && y > feet.getY() && (isStepBlock(state) || y - feet.getY() >= 0.4D);
        return (water || climbing || supported) && clear(level, box(feet.getX() + 0.5D, y, feet.getZ() + 0.5D));
    }

    private static boolean dangerous(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // 气泡柱下方的岩浆块也是伤害面，AI 不应以站上去为电梯入口。
        return state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
    }

    static boolean swimmingAllowed(ServerLevel level) {
        var settings = AreasWorldComponent.KEY.get(level).areasSettings;
        return settings == null || settings.canSimpleSwim && settings.canUnderWater && settings.allowInDeepWater;
    }

    static boolean water(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getFluidState().is(FluidTags.WATER);
    }

    static boolean climbable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    static int lift(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.BUBBLE_COLUMN)) state = level.getBlockState(pos.above());
        return state.is(Blocks.BUBBLE_COLUMN)
                ? (state.getValue(BubbleColumnBlock.DRAG_DOWN)
                        ? FakeSteveTraversalPolicy.LIFT_DOWN : FakeSteveTraversalPolicy.LIFT_UP)
                : FakeSteveTraversalPolicy.WATER;
    }

    /** 逐段检查整个身体扫过的空间；门只在确实能打开时允许规划穿过。 */
    static boolean corridor(ServerLevel level, Vec3 from, Vec3 to) {
        double dy = to.y - from.y;
        double length = from.distanceTo(to);
        if (length > 8.0D || Math.abs(dy) > 3.1D) return false;
        int samples = Math.max(1, (int) Math.ceil(length * 4.0D));
        double travelY = Math.max(from.y, to.y);
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double x = from.x + (to.x - from.x) * t;
            double z = from.z + (to.z - from.z) * t;
            if (!clear(level, box(x, travelY, z))) return false;
            BlockPos support = BlockPos.containing(x, travelY, z);
            if (!water(level, support) && !climbable(level, support)
                    && !hasSupportWithin(level, support, 3)) return false;
        }
        return true;
    }

    static boolean stepSafe(ServerLevel level, Vec3 from, Vec3 horizontal) {
        Vec3 ahead = from.add(horizontal);
        BlockPos feet = BlockPos.containing(ahead);
        return safeStand(level, feet) && corridor(level, from, point(level, normalize(level, feet)));
    }

    private static boolean hasSupportWithin(ServerLevel level, BlockPos pos, int depth) {
        for (int dy = 0; dy <= depth; dy++) {
            BlockPos below = pos.below(dy);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                    || water(level, below) || climbable(level, below)) return true;
        }
        return false;
    }

    private static AABB box(double x, double y, double z) {
        return new AABB(x - HALF_WIDTH, y + 0.001D, z - HALF_WIDTH,
                x + HALF_WIDTH, y + HEIGHT - 0.001D, z + HALF_WIDTH);
    }

    private static boolean clear(ServerLevel level, AABB box) {
        var volume = Shapes.create(box);
        for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(box.minX), (int) Math.floor(box.minY) - 1,
                (int) Math.floor(box.minZ), (int) Math.floor(box.maxX), (int) Math.floor(box.maxY),
                (int) Math.floor(box.maxZ))) {
            if (!level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (passage(level, pos, state)) continue;
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty() && Shapes.joinIsNotEmpty(volume,
                    shape.move(pos.getX(), pos.getY(), pos.getZ()), BooleanOp.AND)) return false;
        }
        return true;
    }

    private static boolean passage(ServerLevel level, BlockPos pos, BlockState state) {
        if (!FakeSteveDoorAccess.isOpenablePassage(state)) return false;
        if (FakeSteveDoorAccess.isOpen(state)) return false; // 打开的门仍有实体门板，按碰撞体绕开。
        if (state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR)) return false;
        if (state.getBlock() instanceof SmallDoorBlock door) {
            BlockPos lower = door.getLowerHalfPos(state, pos);
            if (!(level.getBlockEntity(lower) instanceof SmallDoorBlockEntity entity)
                    || entity.isJammed() || entity.isBlasted()) return false;
            BlockPos anchor = lower.above();
            if (LockEntityManager.getInstance().getLockEntity(anchor) != null) return false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (LockEntityManager.getInstance().getLockEntity(anchor.relative(direction)) != null) return false;
            }
        }
        return true;
    }

    private static final class SearchWorld {
        final ServerLevel level;
        final ServerPlayer mover;
        final BlockPos origin;
        final boolean jumps;
        final Map<BlockPos, Boolean> walkable = new HashMap<>();
        final List<Vec3> crowd;

        SearchWorld(ServerLevel level, ServerPlayer mover) {
            this.level = level;
            this.mover = mover;
            this.origin = mover.blockPosition();
            this.jumps = SREGameWorldComponent.KEY.get(level).isJumpAvailable();
            this.crowd = level.players().stream().filter(p -> p != mover && p.isAlive() && !p.isSpectator())
                    .map(ServerPlayer::position).toList();
        }

        boolean usable(BlockPos pos) {
            if (Math.abs(pos.getX() - origin.getX()) > 96 || Math.abs(pos.getZ() - origin.getZ()) > 96
                    || Math.abs(pos.getY() - origin.getY()) > 64) return false;
            return walkable.computeIfAbsent(pos.immutable(), p -> standable(level, p));
        }

        List<FakeStevePathSearch.Edge<BlockPos>> neighbours(BlockPos current) {
            List<FakeStevePathSearch.Edge<BlockPos>> result = new ArrayList<>(12);
            Vec3 from = point(level, current);
            boolean wet = water(level, current);
            boolean climbing = climbable(level, current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy : VERTICAL_OFFSETS) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!usable(next)) continue;
                        Vec3 target = point(level, next);
                        double rise = target.y - from.y;
                        if (rise > 1.25D || rise < -3.0D) continue;
                        if (rise > 0.6D && !jumps && !wet && !climbing
                                && !isStepBlock(level.getBlockState(next.below()))
                                && !isStepBlock(level.getBlockState(next))) continue;
                        if (dx != 0 && dz != 0 && (!usable(current.offset(dx, dy, 0))
                                || !usable(current.offset(0, dy, dz)))) continue;
                        if (!corridor(level, from, target)) continue;
                        double crowdCost = 0;
                        for (Vec3 player : crowd) {
                            if (Math.abs(player.y - target.y) < 1.8D && player.distanceToSqr(target) < 1.2D)
                                crowdCost += 5.0D;
                        }
                        result.add(new FakeStevePathSearch.Edge<>(next.immutable(),
                                (dx != 0 && dz != 0 ? 1.414D : 1.0D) + Math.abs(rise) * 0.35D
                                        + (water(level, next) ? 0.4D : 0) + crowdCost));
                        break;
                    }
                }
            }
            if (wet || climbing) {
                int column = lift(level, current);
                for (int dy : new int[] {1, -1}) {
                    BlockPos next = current.offset(0, dy, 0);
                    if (!FakeSteveTraversalPolicy.allowsVertical(column, dy) || !usable(next)) continue;
                    // 不能在水外悬空上升；上层必须仍是水或梯子。
                    if (dy > 0 && !water(level, next) && !climbable(level, next)) continue;
                    if (clear(level, box(from.x, point(level, next).y, from.z)))
                        result.add(new FakeStevePathSearch.Edge<>(next.immutable(), column == 0 ? 1.3D : 1.0D));
                }
            }
            return result;
        }
    }
}
