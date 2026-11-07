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

/**
 * 玩家尺寸的三维导航：楼梯、落差、门、梯子和有方向的水电梯共用一张图。
 *
 * <h2>与地图配置的关系</h2>
 * 搜索是否可行必须服从 {@link FakeSteveNavRules}（禁跳 / 禁游 / 最大落差），否则会产出
 * 「地图不允许、身体做不到或会被环境判死」的路线：
 * <ul>
 * <li><b>禁跳</b>：抬升上限从 {@link #MAX_JUMP_RISE} 降到 {@link FakeStevePathPolicy#STEP_HEIGHT}，
 * 楼梯与半砖仍可走（原版自动抬脚不依赖跳跃输入）；</li>
 * <li><b>禁游</b>：水面直接不可站，A* 只能找陆路；找不到就返回「未到达」交给上层退避；</li>
 * <li><b>落差</b>：向下扩展的深度取 {@link FakeSteveNavRules#maxFallDistance()}，
 * 而不是写死的三格。</li>
 * </ul>
 *
 * <h2>性能约定</h2>
 * 本类的每个方法都会被 A* 反复调用，因此<b>方块状态一律走 {@link SearchWorld} 的缓存</b>，
 * 只有 {@code SearchWorld} 内部允许直接访问 {@code ServerLevel}。静态工具方法（
 * {@code standable} / {@code standingY} 等）保留给「一次性」调用方与测试，不参与搜索热路径。
 */
final class FakeSteveNavigator {
    /** 单次搜索的节点预算。原版同样用「最大距离 × 每格最大节点数」，这里直接给固定上界。 */
    private static final int MAX_VISITED = 2048;
    private static final double HALF_WIDTH = 0.305D;
    private static final double HEIGHT = 1.8D;
    /**
     * 同一水平坐标上尝试落脚的高度顺序：同层 → 上一层 → 下一层 → 更深两层。
     *
     * <p>
     * 顺序固定，因此「上层够得着就优先跳上去」这种副作用不会发生；
     * 也保证运行时按同一条规则就能走到规划出来的那个节点。
     */
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1, -2, -3};
    /** 跳跃可以到达的最大抬升（格）：玩家跳跃上限约 1.25 格。 */
    static final double MAX_JUMP_RISE = 1.25D;
    /** 旧网格适配器共用的水平邻接顺序，保留路线判定的确定性。 */
    static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private FakeSteveNavigator() {
    }

    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal) {
        return find(level, mover, goal, false);
    }

    /**
     * 从身体当前位置寻路到 {@code goal}。
     *
     * @param explicitTarget 追击/潜行/同化这类「明确目标」会放宽节点预算
     * @return 路点队列（第一个是下一个要走到的格子）；无路可走时返回空队列
     */
    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal,
            boolean explicitTarget) {
        return search(level, mover, goal, explicitTarget).path();
    }

    /**
     * 与 {@link #find} 相同，但额外返回「是否真的到达目标」与展开节点数。
     *
     * <p>
     * {@code reached == false} 时返回的 path 只是「离目标最近的一段」，调用方必须重新寻路，
     * 不能把它当成已经到达——否则身体会停在半路却以为自己到点了。
     */
    static Result search(ServerLevel level, ServerPlayer mover, BlockPos goal,
            boolean explicitTarget) {
        return search(new SearchWorld(level, mover), goal, explicitTarget);
    }

    /**
     * 复用同一个 {@link SearchWorld} 再搜一次。
     *
     * <p>
     * 巡逻时会对多个候选目标连续寻路，候选之间方块大量重叠。共用一份缓存可以把
     * 「每个候选都重新读一遍方块」降成「整轮只读一遍」，这是伪人 AI 上最划算的一处优化。
     */
    static Result search(SearchWorld world, BlockPos goal, boolean explicitTarget) {
        BlockPos start = world.normalize(world.origin());
        BlockPos destination = world.normalize(goal);
        var result = FakeStevePathSearch.find(start, destination::equals,
                pos -> heuristic(pos, destination), world::neighbours,
                explicitTarget ? MAX_VISITED * 2 : MAX_VISITED);
        return new Result(simplify(world, result.path()), result.complete(), result.visited());
    }

    /**
     * 一次搜索的结果。
     *
     * @param path    路点（已做共线合并，不含起点）
     * @param reached 是否真的到达了目标；false 时 path 是「离目标最近的一段」
     * @param visited 本次展开的节点数，用于调参和发现「预算被打满」这类配置问题
     */
    record Result(ArrayDeque<BlockPos> path, boolean reached, int visited) {
    }

    static boolean reaches(ArrayDeque<BlockPos> path, BlockPos goal) {
        BlockPos last = path.peekLast();
        return last != null && last.equals(goal);
    }

    /**
     * 欧几里得启发式（可采纳）。
     *
     * <p>
     * 旧实现用 {@code max(dx, dz, |dy|/3)}，在「需要绕远」的迷宫里严重低估，
     * 于是把大量等价节点都压到同一个 f 值上，开放集迅速膨胀——这正是一次巡逻要跑
     * 4 次全量 A* 时最贵的部分。这里改用真正的三维欧氏距离：仍然可采纳，
     * 但在开阔区域能把 f 值拉开，明显减少同分节点。
     */
    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
        return new SearchWorld(level, null).standable(feet);
    }

    static boolean swimmingAllowed(ServerLevel level) {
        return FakeSteveNavRules.of(level).canSwim();
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
        return new SearchWorld(level, null).corridor(from, to);
    }

    static boolean stepSafe(ServerLevel level, Vec3 from, Vec3 horizontal) {
        SearchWorld world = new SearchWorld(level, null);
        Vec3 ahead = from.add(horizontal);
        BlockPos feet = BlockPos.containing(ahead);
        return world.standable(feet) && world.corridor(from, point(level, world.normalize(feet)));
    }

    /**
     * 共线合并：三点在同一条直线上时删掉中间那个，只保留拐点、门与台阶。
     *
     * <p>
     * 目的不是「省内存」，而是省掉<b>运行时的朝向修正</b>：路点越密，身体每格都要重新
     * 对齐 yaw，走起来就左右抖。合并后一条走廊通常只剩一个终点。
     *
     * <p>
     * 用叉积判断<b>方向</b>而不是比较两段位移是否相等——合并过程中前一个保留点可能已经很远，
     * 「步长相同」永远不成立，那样整条直线都简化不掉。
     */
    private static ArrayDeque<BlockPos> simplify(SearchWorld world, List<BlockPos> nodes) {
        ArrayDeque<BlockPos> result = new ArrayDeque<>(nodes.size());
        if (nodes.size() <= 2) {
            result.addAll(nodes);
            return result;
        }
        List<BlockPos> kept = new ArrayList<>(nodes.size());
        kept.add(nodes.get(0));
        for (int i = 1; i < nodes.size() - 1; i++) {
            BlockPos previous = kept.get(kept.size() - 1);
            BlockPos current = nodes.get(i);
            BlockPos next = nodes.get(i + 1);
            if (collinear(previous, current, next) && !world.preserveNode(current)) continue;
            kept.add(current);
        }
        kept.add(nodes.get(nodes.size() - 1));
        for (BlockPos pos : kept) {
            result.addLast(pos.immutable());
        }
        return result;
    }

    private static boolean collinear(BlockPos a, BlockPos b, BlockPos c) {
        int abx = b.getX() - a.getX();
        int aby = b.getY() - a.getY();
        int abz = b.getZ() - a.getZ();
        int bcx = c.getX() - b.getX();
        int bcy = c.getY() - b.getY();
        int bcz = c.getZ() - b.getZ();
        int crossX = aby * bcz - abz * bcy;
        int crossY = abz * bcx - abx * bcz;
        int crossZ = abx * bcy - aby * bcx;
        if (crossX != 0 || crossY != 0 || crossZ != 0) return false;
        // 点积 > 0 排除「原路折返」（夹角 180°，叉积同样为零）。
        return abx * bcx + aby * bcy + abz * bcz > 0;
    }

    /**
     * 一次搜索的世界视图，等价于原版的 {@code NodeEvaluator} + 方块缓存。
     *
     * <p>
     * A* 会把同一个格子当成不同节点的邻居反复询问（一个格子最多被 8 个方向 × 5 个高度
     * 命中 40 次），所以这里对「方块状态 / 碰撞形状 / 站立高度 / 危险判定」全部做记忆化，
     * 生命周期等于一次搜索。搜索结束后随实例被 GC，不会跨 tick 残留。
     */
    static final class SearchWorld {
        private final ServerLevel level;
        private final ServerPlayer mover;
        private final BlockPos origin;
        private final FakeSteveNavRules rules;
        /** 其他玩家的位置快照：每次搜索取一次，避免在邻居循环里反复遍历玩家列表。 */
        private final List<Vec3> crowd;

        private final Map<BlockPos, Boolean> standableCache = new HashMap<>();
        private final Map<BlockPos, Double> heightCache = new HashMap<>();
        private final Map<BlockPos, Boolean> waterCache = new HashMap<>();
        private final Map<BlockPos, Boolean> climbableCache = new HashMap<>();
        private final Map<BlockPos, Boolean> dangerousCache = new HashMap<>();
        private final Map<BlockPos, Boolean> stepCache = new HashMap<>();
        private final Map<BlockPos, Boolean> passageCache = new HashMap<>();

        SearchWorld(ServerLevel level, ServerPlayer mover) {
            this.level = level;
            this.mover = mover;
            this.origin = mover != null ? mover.blockPosition() : null;
            this.rules = FakeSteveNavRules.of(level, mover);
            this.crowd = mover == null ? List.of()
                    : level.players().stream()
                            .filter(p -> p != mover && p.isAlive() && !p.isSpectator())
                            .map(ServerPlayer::position).toList();
        }

        /** 本次搜索的起点格（搜索半径与起终点归一化都以它为基准）。 */
        BlockPos origin() {
            return origin;
        }

        FakeSteveNavRules rules() {
            return rules;
        }

        /** 搜索半径：超出这个范围的节点没有意义，提前剪掉能省下大量方块读取。 */
        boolean inRange(BlockPos pos) {
            if (origin == null) return true;
            return Math.abs(pos.getX() - origin.getX()) <= 96
                    && Math.abs(pos.getZ() - origin.getZ()) <= 96
                    && Math.abs(pos.getY() - origin.getY()) <= 64;
        }

        BlockPos normalize(BlockPos pos) {
            for (int dy : new int[] {0, 1, -1, -2}) {
                BlockPos candidate = pos.offset(0, dy, 0);
                if (standable(candidate)) return candidate.immutable();
            }
            return pos.immutable();
        }

        double standingY(BlockPos feet) {
            Double cached = heightCache.get(feet);
            if (cached != null) return cached;
            double height = FakeSteveNavigator.standingY(level, feet);
            heightCache.put(feet, height);
            return height;
        }

        Vec3 point(BlockPos feet) {
            return new Vec3(feet.getX() + 0.5D, standingY(feet), feet.getZ() + 0.5D);
        }

        boolean water(BlockPos pos) {
            return waterCache.computeIfAbsent(pos.immutable(), p ->
                    level.getBlockState(p).getFluidState().is(FluidTags.WATER));
        }

        boolean climbable(BlockPos pos) {
            return climbableCache.computeIfAbsent(pos.immutable(), p ->
                    level.getBlockState(p).is(BlockTags.CLIMBABLE));
        }

        boolean stepBlock(BlockPos pos) {
            return stepCache.computeIfAbsent(pos.immutable(), p ->
                    isStepBlock(level.getBlockState(p)));
        }

        boolean dangerous(BlockPos pos) {
            return dangerousCache.computeIfAbsent(pos.immutable(), p -> {
                BlockState state = level.getBlockState(p);
                // 气泡柱下方的岩浆块也是伤害面，AI 不应以站上去为电梯入口。
                return state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.FIRE)
                        || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                        || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                        || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
            });
        }

        /** 路径简化时必须保留的节点：门（要停下来开）与台阶（要抬脚）。 */
        boolean preserveNode(BlockPos feet) {
            return passage(feet) || stepBlock(feet);
        }

        boolean standable(BlockPos feet) {
            Boolean cached = standableCache.get(feet);
            if (cached != null) return cached;
            boolean result = evaluate(feet);
            standableCache.put(feet.immutable(), result);
            return result;
        }

        private boolean evaluate(BlockPos feet) {
            if (!level.hasChunkAt(feet) || level.isOutsideBuildHeight(feet)
                    || level.isOutsideBuildHeight(feet.above())) return false;
            if (dangerous(feet) || dangerous(feet.below())) return false;
            double y = standingY(feet);
            // 禁游地图：水面既不能作为路线，也不能作为落点。
            boolean water = water(feet) || water(feet.above());
            if (water && !rules.canSwim()) return false;
            boolean climbing = climbable(feet);
            BlockPos belowPos = feet.below();
            var below = level.getBlockState(belowPos).getCollisionShape(level, belowPos);
            var own = level.getBlockState(feet).getCollisionShape(level, feet);
            boolean supported = !below.isEmpty() && below.max(Direction.Axis.Y) >= 0.45D
                    || !own.isEmpty() && y > feet.getY()
                            && (stepBlock(feet) || y - feet.getY() >= 0.4D);
            return (water || climbing || supported)
                    && clear(box(feet.getX() + 0.5D, y, feet.getZ() + 0.5D));
        }

        /**
         * 一步是否可以走：类型可通过、抬升不超过跳跃能力、落差不超过地图允许的最大落差。
         *
         * <p>
         * 三类地图约束都在这里落地，因为它们全都是「这一步能不能走」的问题。
         * 禁跳时抬升上限降到 0.6 格，但楼梯/半砖仍放行——这与禁跳地图里真人的可行走范围一致。
         */
        boolean canStep(BlockPos from, BlockPos to, double rise, boolean wet, boolean climbing) {
            if (rise > MAX_JUMP_RISE) return false;
            if (rise < -rules.maxFallDistance()) return false;
            if (rise > FakeStevePathPolicy.STEP_HEIGHT && !rules.canJump() && !wet && !climbing) {
                // 禁跳：只保留「原版不需要跳跃输入就能上去」的高度。
                return FakeStevePathPolicy.canAscendWithoutJump(rise, stepBlock(to))
                        || FakeStevePathPolicy.canAscendWithoutJump(rise, stepBlock(to.below()));
            }
            return true;
        }

        List<FakeStevePathSearch.Edge<BlockPos>> neighbours(BlockPos current) {
            List<FakeStevePathSearch.Edge<BlockPos>> result = new ArrayList<>(12);
            Vec3 from = point(current);
            boolean wet = water(current);
            boolean climbing = climbable(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy : VERTICAL_OFFSETS) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!inRange(next) || !standable(next)) continue;
                        Vec3 target = point(next);
                        double rise = target.y - from.y;
                        if (!canStep(current, next, rise, wet, climbing)) continue;
                        if (dx != 0 && dz != 0 && (!standable(current.offset(dx, dy, 0))
                                || !standable(current.offset(0, dy, dz)))) continue;
                        if (!corridor(from, target)) continue;
                        result.add(new FakeStevePathSearch.Edge<>(next.immutable(),
                                (dx != 0 && dz != 0 ? 1.414D : 1.0D) + Math.abs(rise) * 0.35D
                                        + (water(next) ? 0.4D : 0) + crowdCost(target)));
                        break;
                    }
                }
            }
            if (wet || climbing) {
                int column = lift(level, current);
                for (int dy : new int[] {1, -1}) {
                    BlockPos next = current.offset(0, dy, 0);
                    if (!FakeSteveTraversalPolicy.allowsVertical(column, dy) || !standable(next)) {
                        continue;
                    }
                    // 不能在水外悬空上升；上层必须仍是水或梯子。
                    if (dy > 0 && !water(next) && !climbable(next)) continue;
                    if (clear(box(from.x, point(next).y, from.z))) {
                        result.add(new FakeStevePathSearch.Edge<>(next.immutable(),
                                column == 0 ? 1.3D : 1.0D));
                    }
                }
            }
            return result;
        }

        private double crowdCost(Vec3 target) {
            double cost = 0;
            for (Vec3 player : crowd) {
                if (Math.abs(player.y - target.y) < 1.8D && player.distanceToSqr(target) < 1.2D) {
                    cost += 5.0D;
                }
            }
            return cost;
        }

        boolean corridor(Vec3 from, Vec3 to) {
            double dy = to.y - from.y;
            double length = from.distanceTo(to);
            if (length > 8.0D || Math.abs(dy) > 3.1D) return false;
            int samples = Math.max(1, (int) Math.ceil(length * 4.0D));
            double travelY = Math.max(from.y, to.y);
            for (int i = 0; i <= samples; i++) {
                double t = (double) i / samples;
                double x = from.x + (to.x - from.x) * t;
                double z = from.z + (to.z - from.z) * t;
                if (!clear(box(x, travelY, z))) return false;
                BlockPos support = BlockPos.containing(x, travelY, z);
                if (!water(support) && !climbable(support) && !hasSupportWithin(support, 3)) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasSupportWithin(BlockPos pos, int depth) {
            for (int dy = 0; dy <= depth; dy++) {
                BlockPos below = pos.below(dy);
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                        || water(below) || climbable(below)) return true;
            }
            return false;
        }

        private AABB box(double x, double y, double z) {
            return new AABB(x - HALF_WIDTH, y + 0.001D, z - HALF_WIDTH,
                    x + HALF_WIDTH, y + HEIGHT - 0.001D, z + HALF_WIDTH);
        }

        private boolean clear(AABB box) {
            var volume = Shapes.create(box);
            for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(box.minX),
                    (int) Math.floor(box.minY) - 1, (int) Math.floor(box.minZ),
                    (int) Math.floor(box.maxX), (int) Math.floor(box.maxY),
                    (int) Math.floor(box.maxZ))) {
                if (!level.hasChunkAt(pos)) return false;
                if (passage(pos)) continue;
                BlockState state = level.getBlockState(pos);
                var shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(volume,
                        shape.move(pos.getX(), pos.getY(), pos.getZ()), BooleanOp.AND)) return false;
            }
            return true;
        }

        /** 门是否真的能打开：锁住、卡死、被炸变形、以及铁门都不算通路。 */
        boolean passage(BlockPos pos) {
            return passageCache.computeIfAbsent(pos.immutable(), p -> {
                BlockState state = level.getBlockState(p);
                if (!FakeSteveDoorAccess.isOpenablePassage(state)) return false;
                if (FakeSteveDoorAccess.isOpen(state)) return false; // 打开的门仍有实体门板，按碰撞体绕开。
                if (state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR)) return false;
                if (state.getBlock() instanceof SmallDoorBlock door) {
                    BlockPos lower = door.getLowerHalfPos(state, p);
                    if (!(level.getBlockEntity(lower) instanceof SmallDoorBlockEntity entity)
                            || entity.isJammed() || entity.isBlasted()) return false;
                    BlockPos anchor = lower.above();
                    if (LockEntityManager.getInstance().getLockEntity(anchor) != null) return false;
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        if (LockEntityManager.getInstance().getLockEntity(anchor.relative(direction)) != null) {
                            return false;
                        }
                    }
                }
                return true;
            });
        }
    }
}
