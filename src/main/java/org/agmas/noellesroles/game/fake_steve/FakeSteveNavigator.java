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
import java.util.function.Predicate;

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
 * <h2>与原版 {@code PathFinder} 的裁剪参数对应</h2>
 * 原版寻路的开销几乎全部由四个参数决定，这里逐个对齐（详见 {@code docs/fake-steve-pathfinding.md}）：
 * <ul>
 * <li><b>最大距离 {@code l}</b> → {@link SearchWorld#prepare} 里的 {@code maxPathDistance}，
 * 累计<b>几何路径距离</b>超过它就剪掉；</li>
 * <li><b>查找边距 {@code n}</b> → 搜索半径 {@code l + n}，超出半径的节点连方块都不读；</li>
 * <li><b>到达范围 {@code r}</b> → {@link #arrival}，目标格不可站时接受曼哈顿 ≤ 1 的邻格，
 * 避免「目标永远不可达」导致每 30 tick 空转重算；</li>
 * <li><b>最大访问节点数 {@code c = l × v}</b> → {@link #budgetFor}，预算随目标距离缩放，
 * 近处巡逻不再固定烧掉满额预算。</li>
 * </ul>
 *
 * <h2>性能约定</h2>
 * 本类的每个方法都会被 A* 反复调用，因此<b>方块状态与碰撞形状一律走 {@link SearchWorld} 的缓存</b>，
 * 只有 {@code SearchWorld} 内部允许直接访问 {@code ServerLevel}。静态工具方法（
 * {@code standable} / {@code standingY} 等）保留给「一次性」调用方与测试，不参与搜索热路径。
 */
final class FakeSteveNavigator {
    /** 单次搜索的节点预算上限。原版是「最大距离 × 最大访问节点比率」，这里给硬上界兜底。 */
    private static final int MAX_VISITED = 2048;
    /**
     * 每格水平距离折算的节点预算。
     *
     * <p>
     * 原版 {@code c = l × v}：搜索范围越大，允许展开的节点越多。伪人的巡逻目标通常只有十几格，
     * 用固定 2048 会让短距离搜索也保留一份满额预算的开放集——这是纯浪费。
     */
    private static final int VISITED_PER_BLOCK = 48;
    /** 预算下限：再近的目标也要留出绕开一堵墙的余地。 */
    private static final int MIN_VISITED = 256;
    private static final double HALF_WIDTH = 0.305D;
    private static final double HEIGHT = 1.8D;
    /** 搜索半径的硬上限（切比雪夫），防止异常目标把整片区块读进来。 */
    private static final double HARD_RADIUS = 96.0D;
    /** 垂直搜索半径的硬上限。 */
    private static final double HARD_VERTICAL_RADIUS = 32.0D;
    /** 查找边距 {@code n}：在「最大距离」之外额外放宽的格数。 */
    private static final double SEARCH_MARGIN = 16.0D;
    /** 最大路径距离相对直线距离的倍数：允许绕远 60%，再多就不值得找了。 */
    private static final double DETOUR_FACTOR = 1.6D;
    /** 最大路径距离的附加余量，保证「贴脸目标」也有足够余量绕过一格厚的墙。 */
    private static final double DETOUR_SLACK = 8.0D;
    /**
     * 同一水平坐标上尝试落脚的高度顺序：同层 → 上一层 → 下一层 → 更深两层。
     *
     * <p>
     * 顺序固定，因此「上层够得着就优先跳上去」这种副作用不会发生；也保证运行时按同一条规则
     * 就能走到规划出来的那个节点。原版同样「同一水平坐标只选最低的可达位置」。
     */
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1, -2, -3};
    /** 危险邻居（火/岩浆/伤害方块）的额外代价，对齐原版 {@code FIRE_IN_NEIGHBOR} 的 8。 */
    static final double DAMAGING_NEIGHBOR_COST = 8.0D;
    /** 水岸（相邻格是水）的额外代价，对齐原版 {@code WATER_BORDER} 的 8。 */
    static final double WATER_BORDER_COST = 8.0D;
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
        return search(world, world.origin(), goal, explicitTarget);
    }

    /**
     * 指定起点再搜一次。
     *
     * <p>
     * 生产调用永远从 {@link SearchWorld#origin()} 出发；网格单元测试没有「身体位置」，
     * 需要显式给起点，否则只能为了测试造一个假玩家。
     */
    static Result search(SearchWorld world, BlockPos start, BlockPos goal, boolean explicitTarget) {
        BlockPos from = world.normalize(start);
        BlockPos destination = world.normalize(goal);
        world.prepare(from, destination, explicitTarget);
        var result = FakeStevePathSearch.find(from, arrival(world, destination),
                pos -> heuristic(pos, destination), world::neighbours,
                world.budget(), world.maxPathDistance());
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
     * 到达判定，对应原版的「到达范围 {@code r}」。
     *
     * <p>
     * 原版只在<b>曼哈顿距离 ≤ r</b> 时才算到达；{@code r} 对玩家是 1。之前这里用
     * {@code destination::equals}，只要目标格本身不可站（任务方块在半空、被玩家占住、
     * 被门挡住），A* 就永远「未到达」，上层每 30 tick 重新搜一遍，白白烧掉整份预算。
     *
     * <p>
     * 所以：目标可站时仍要求精确相等（路线必须真的走到那一格）；目标不可站时放宽到
     * 曼哈顿 ≤ 1，让身体走到它旁边就算到位——这与真人「目标够不着就站到最近处」一致。
     */
    private static Predicate<BlockPos> arrival(SearchWorld world, BlockPos destination) {
        if (world.standable(destination)) {
            return destination::equals;
        }
        return pos -> pos.distManhattan(destination) <= 1;
    }

    /**
     * 欧几里得启发式（可采纳）。
     *
     * <p>
     * 旧实现用 {@code max(dx, dz, |dy|/3)}，在「需要绕远」的迷宫里严重低估，
     * 于是把大量等价节点都压到同一个 f 值上，开放集迅速膨胀——这正是一次巡逻要跑
     * 4 次全量 A* 时最贵的部分。这里改用真正的三维欧氏距离：仍然可采纳，
     * 但在开阔区域能把 f 值拉开，明显减少同分节点。
     *
     * <p>
     * 原版会再乘 1.5 换取速度（并因此放弃最优性）。这里<b>不</b>乘：伪人的路线会被运行时
     * 的「贴墙切角 / 门 / 电梯」逐段校验，宁可多展开几个节点也要拿到一条真的能走完的路。
     */
    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static BlockPos normalize(ServerLevel level, BlockPos pos) {
        return new SearchWorld(level, null).normalize(pos);
    }

    /**
     * 目标不可站时退到最近的可站邻格，让运行时的「到达」判定与 A* 的到达范围一致。
     *
     * <p>
     * {@link #arrival} 允许 A* 在目标不可站时停在曼哈顿 ≤ 1 的邻格。若运行时仍拿原始
     * 目标坐标当终点，身体走完路线也永远「没到」，于是每 30 tick 重算一次、每次烧掉整份预算。
     * 这里把终点换成一个真的能站的格子，两边就对齐了。
     */
    static BlockPos approach(ServerLevel level, BlockPos goal) {
        SearchWorld world = new SearchWorld(level, null);
        BlockPos destination = world.normalize(goal);
        if (world.standable(destination)) return destination;
        BlockPos best = destination;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : HORIZONTAL) {
            BlockPos side = destination.relative(direction);
            if (!world.standable(side)) continue;
            double distance = side.distSqr(goal);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = side;
            }
        }
        return best.immutable();
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

        /** 非空时表示这是一次「网格图」搜索（单元测试专用），方块来源全部走它。 */
        private final FakeSteveNavWorld grid;
        private final Map<BlockPos, Boolean> standableCache = new HashMap<>();
        private final Map<BlockPos, Double> heightCache = new HashMap<>();
        private final Map<BlockPos, Boolean> waterCache = new HashMap<>();
        private final Map<BlockPos, Boolean> climbableCache = new HashMap<>();
        private final Map<BlockPos, Boolean> dangerousCache = new HashMap<>();
        /** 危险邻居 / 水岸的代价缓存：每个节点只扫一次 24 邻域。 */
        private final Map<BlockPos, Double> malusCache = new HashMap<>();
        private final Map<BlockPos, Boolean> stepCache = new HashMap<>();
        private final Map<BlockPos, Boolean> passageCache = new HashMap<>();
        /**
         * 碰撞形状缓存。
         *
         * <p>
         * {@code clear()} 与 {@code hasSupportWithin()} 每次都要读一片方块的碰撞形状，
         * 而 {@code corridor()} 又对每条边调用一次——这是搜索里最贵的一环。碰撞形状在一次搜索内
         * 不会变，缓存下来能把重复读取从「每条边几十次」压到「每个方块一次」。
         */
        private final Map<BlockPos, VoxelShape> shapeCache = new HashMap<>();

        /** 本次搜索的起点（搜索半径以它为基准）；为 {@code null} 时不做半径裁剪。 */
        private BlockPos center;
        /** 搜索半径（切比雪夫，水平与垂直共用），由 {@link #prepare} 按目标距离算出。 */
        private double radius = HARD_RADIUS;
        /** 本次搜索允许的累计几何路径距离（原版的最大距离 {@code l}）。 */
        private double maxPathDistance;
        /** 本次搜索允许展开的节点数（原版的最大访问节点数 {@code c}）。 */
        private int budget = MAX_VISITED;
        /** 向下扩展的深度，随地图允许的最大落差收缩。 */
        private int[] verticalOffsets = VERTICAL_OFFSETS;

        SearchWorld(ServerLevel level, ServerPlayer mover) {
            this.level = level;
            this.mover = mover;
            this.origin = mover != null ? mover.blockPosition() : null;
            this.rules = FakeSteveNavRules.of(level, mover);
            this.grid = null;
            this.crowd = mover == null ? List.of()
                    : level.players().stream()
                            .filter(p -> p != mover && p.isAlive() && !p.isSpectator())
                            .map(ServerPlayer::position).toList();
        }

        /** 只用于测试的构造器：把方块来源换成网格图，其余逻辑与生产完全一致。 */
        SearchWorld(FakeSteveNavWorld grid, FakeSteveNavRules rules) {
            this.level = null;
            this.mover = null;
            this.origin = null;
            this.rules = rules;
            this.grid = grid;
            this.crowd = List.of();
        }

        /** 本次搜索的起点格（搜索半径与起终点归一化都以它为基准）。 */
        BlockPos origin() {
            return origin;
        }

        FakeSteveNavRules rules() {
            return rules;
        }

        /**
         * 按「起点 → 目标」的距离设置本次搜索的三个裁剪参数，对应原版
         * {@code PathFinder#findPath} 的 {@code l} / {@code n} / {@code c}。
         *
         * <p>
         * 一次性算好而不是每个节点现算：{@code inRange} 会被每个邻居调用（一次搜索上万次），
         * 里面不能有任何除整数比较以外的工作。
         */
        void prepare(BlockPos start, BlockPos goal, boolean explicitTarget) {
            this.center = start;
            double distance = Math.sqrt(start.distSqr(goal));
            this.maxPathDistance = distance * DETOUR_FACTOR + DETOUR_SLACK;
            this.radius = Math.min(HARD_RADIUS, distance + SEARCH_MARGIN);
            this.budget = budgetFor(distance, explicitTarget);
            this.verticalOffsets = verticalOffsets(rules);
        }

        /** 原版 {@code c = l × v}：预算随搜索范围缩放，近处目标不再吃掉满额预算。 */
        private static int budgetFor(double distance, boolean explicitTarget) {
            int cap = explicitTarget ? MAX_VISITED * 2 : MAX_VISITED;
            int scaled = (int) Math.round(distance * VISITED_PER_BLOCK) + MIN_VISITED;
            return Math.max(MIN_VISITED, Math.min(cap, scaled));
        }

        /**
         * 向下扩展的深度：同层、上一层，然后按地图允许的落差向下。
         *
         * <p>
         * 写死 {@code -3} 有两个问题：落差被限制成 0 的地图上白跑两轮 {@code standable}；
         * 而 {@code fallToDeathHeight} 允许更大落差时又探不到底。这里按规则裁剪。
         */
        private static int[] verticalOffsets(FakeSteveNavRules rules) {
            int depth = (int) Math.max(1.0D, Math.min(3.0D, Math.floor(rules.maxFallDistance())));
            int[] offsets = new int[2 + depth];
            offsets[0] = 0;
            offsets[1] = 1;
            for (int i = 0; i < depth; i++) {
                offsets[2 + i] = -(i + 1);
            }
            return offsets;
        }

        double maxPathDistance() {
            return maxPathDistance;
        }

        int budget() {
            return budget;
        }

        /** 搜索半径：超出这个范围的节点没有意义，提前剪掉能省下大量方块读取。 */
        boolean inRange(BlockPos pos) {
            if (center == null) return true;
            double dy = Math.min(radius, HARD_VERTICAL_RADIUS);
            return Math.abs(pos.getX() - center.getX()) <= radius
                    && Math.abs(pos.getZ() - center.getZ()) <= radius
                    && Math.abs(pos.getY() - center.getY()) <= dy;
        }

        /**
         * 把任意位置吸附到「真正能站的那一格」。
         *
         * <p>
         * 先沿竖直方向找（原版起点算法的第一、二步）；都不行时退到<b>判定箱的 4 个水平角落</b>
         * （原版起点算法的第三步）。加这一步是因为 AI 提交的坐标常常正好落在墙角里：
         * 中心格是墙，但斜角那格完全能站，旧实现会直接放弃整次寻路。
         */
        BlockPos normalize(BlockPos pos) {
            for (int dy : new int[] {0, 1, -1, -2}) {
                BlockPos candidate = pos.offset(0, dy, 0);
                if (standable(candidate)) return candidate.immutable();
            }
            for (int dx : new int[] {-1, 1}) {
                for (int dz : new int[] {-1, 1}) {
                    BlockPos candidate = pos.offset(dx, 0, dz);
                    if (standable(candidate)) return candidate.immutable();
                }
            }
            return pos.immutable();
        }

        double standingY(BlockPos feet) {
            Double cached = heightCache.get(feet);
            if (cached != null) return cached;
            double height = grid != null ? grid.standY(feet) : FakeSteveNavigator.standingY(level, feet);
            heightCache.put(feet, height);
            return height;
        }

        Vec3 point(BlockPos feet) {
            return new Vec3(feet.getX() + 0.5D, standingY(feet), feet.getZ() + 0.5D);
        }

        boolean water(BlockPos pos) {
            return waterCache.computeIfAbsent(pos.immutable(), p ->
                    grid != null ? grid.isWater(p)
                            : level.getBlockState(p).getFluidState().is(FluidTags.WATER));
        }

        boolean climbable(BlockPos pos) {
            return climbableCache.computeIfAbsent(pos.immutable(), p ->
                    grid == null && level.getBlockState(p).is(BlockTags.CLIMBABLE));
        }

        boolean stepBlock(BlockPos pos) {
            return stepCache.computeIfAbsent(pos.immutable(), p ->
                    grid != null ? grid.stepBlock(p) : isStepBlock(level.getBlockState(p)));
        }

        boolean dangerous(BlockPos pos) {
            return dangerousCache.computeIfAbsent(pos.immutable(), p -> {
                if (grid != null) return grid.isDangerous(p);
                BlockState state = level.getBlockState(p);
                // 气泡柱下方的岩浆块也是伤害面，AI 不应以站上去为电梯入口。
                return state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.FIRE)
                        || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                        || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                        || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
            });
        }

        /** 碰撞形状（带缓存）：搜索里唯一允许读 {@code ServerLevel} 的入口之一。 */
        private VoxelShape shape(BlockPos pos) {
            if (grid != null) return Shapes.block();
            return shapeCache.computeIfAbsent(pos.immutable(),
                    p -> level.getBlockState(p).getCollisionShape(level, p));
        }

        /**
         * 周围 24 格的「危险邻居」代价，对应原版 {@code FIRE_IN_NEIGHBOR} /
         * {@code DAMAGING_IN_NEIGHBOR} / {@code WATER_BORDER}。
         *
         * <p>
         * 原版对每个节点的 24 邻域（不含自身、上方、下方）扫一遍，遇到火/岩浆、伤害方块、
         * 水就给出 8 的额外代价。这里只做「有危险邻居就加固定代价」的近似——本模组不需要
         * 区分具体是哪种危险，重点是让 A* 愿意从岩浆块旁边绕开，而不是擦着走过去。
         *
         * <p>
         * 结果按节点缓存，避免同一次搜索里反复扫同一片邻域。
         */
        double neighbourMalus(BlockPos feet) {
            Double cached = malusCache.get(feet);
            if (cached != null) return cached;
            double malus = 0.0D;
            for (int dx = -1; dx <= 1 && malus == 0.0D; dx++) {
                for (int dy = -1; dy <= 1 && malus == 0.0D; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        // 原版排除「正上 / 正下」，只扫同一层的斜向与侧向。
                        if (dx == 0 && dz == 0) continue;
                        BlockPos side = feet.offset(dx, dy, dz);
                        if (dangerous(side)) {
                            malus = DAMAGING_NEIGHBOR_COST;
                            break;
                        }
                        if (water(side)) malus = WATER_BORDER_COST;
                    }
                }
            }
            malusCache.put(feet.immutable(), malus);
            return malus;
        }

        /** 路径简化时必须保留的节点：门（要停下来开）与台阶（要抬脚）。 */
        boolean preserveNode(BlockPos feet) {
            if (grid != null) return grid.preserveNode(feet);
            return passage(feet) || stepBlock(feet);
        }

        boolean standable(BlockPos feet) {
            Boolean cached = standableCache.get(feet);
            if (cached != null) return cached;
            boolean result = grid != null ? grid.standable(feet) && swimmable(feet) : evaluate(feet);
            standableCache.put(feet.immutable(), result);
            return result;
        }


        /** 禁游地图：水面既不能作为路线，也不能作为落点（生产与网格两条路径共用）。 */
        private boolean swimmable(BlockPos feet) {
            if (rules.canSwim()) return true;
            return !water(feet) && !water(feet.above());
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
            VoxelShape below = shape(feet.below());
            VoxelShape own = shape(feet);
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

        /**
         * 斜向切角：对应原版「对角中间的两个位置的方块高度不能高于当前节点和对角节点」。
         *
         * <p>
         * 旧实现要求两个直角列在<b>目标那一层</b>都真的能站，这比原版严格得多——贴墙斜走时
         * 直角列往往只是「这一层站不了但上一层/下一层能走」，于是整片沿墙的斜向移动被封死，
         * 身体只能一格一格地拐直角，看起来像在蹭墙。
         *
         * <p>
         * 现在的判定是「这一列在不超过两端节点高度的位置上有可通行面」，既拦住穿墙斜切，
         * 又放行贴墙斜走。
         */
        private boolean cornerPassable(BlockPos column, double limitY) {
            if (grid != null) return grid.canCutCorner(column, limitY);
            double ceiling = limitY + FakeStevePathPolicy.STEP_HEIGHT;
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos feet = column.offset(0, dy, 0);
                if (standable(feet) && standingY(feet) <= ceiling) return true;
            }
            return false;
        }

        List<FakeStevePathSearch.Edge<BlockPos>> neighbours(BlockPos current) {
            List<FakeStevePathSearch.Edge<BlockPos>> result = new ArrayList<>(12);
            Vec3 from = point(current);
            boolean wet = water(current);
            boolean climbing = climbable(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy : verticalOffsets) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!inRange(next) || !standable(next)) continue;
                        Vec3 target = point(next);
                        double rise = target.y - from.y;
                        if (!canStep(current, next, rise, wet, climbing)) continue;
                        if (dx != 0 && dz != 0) {
                            // 斜向：两个直角列都要能在「不超过两端高度」的位置通过。
                            double limitY = Math.max(current.getY(), next.getY());
                            if (!cornerPassable(current.offset(dx, 0, 0), limitY)
                                    || !cornerPassable(current.offset(0, 0, dz), limitY)) continue;
                        }
                        // 相邻两格之间没有「夹在中间」的方块：两端的全身体积判定
                        // （standable → clear）已经把墙挡在外面。旧实现在这里再跑一次
                        // corridor 扫掠，每条边要采样 6 次、每次读十几个方块形状，
                        // 是整条搜索里最贵的一环，且对相邻移动完全冗余。
                        // corridor 仍然保留给运行时（FakeSteveNavigation）与跨格校验。
                        double length = dx != 0 && dz != 0 ? Math.sqrt(2.0D) : 1.0D;
                        result.add(new FakeStevePathSearch.Edge<>(next.immutable(),
                                length + Math.abs(rise) * 0.35D
                                        + (water(next) ? 0.4D : 0.0D) + crowdCost(target)
                                        + neighbourMalus(next),
                                length + Math.abs(rise)));
                        break;
                    }
                }
            }
            // 禁游地图里湿节点根本不会被展开（standable 已经拒绝），这里再挡一次，
            // 避免「禁游却按跳跃上浮」这种寻路认可、输入按不下去的分歧。
            if ((wet || climbing) && (rules.canSwim() || climbing)) {
                // 网格模式没有气泡柱：竖直移动只由 FakeSteveTraversalPolicy 的纯规则决定。
                int column = grid != null ? FakeSteveTraversalPolicy.WATER : lift(level, current);
                for (int dy : new int[] {1, -1}) {
                    BlockPos next = current.offset(0, dy, 0);
                    if (!FakeSteveTraversalPolicy.allowsVertical(column, dy) || !standable(next)) {
                        continue;
                    }
                    // 不能在水外悬空上升；上层必须仍是水或梯子。
                    if (dy > 0 && !water(next) && !climbable(next)) continue;
                    if (clear(box(from.x, point(next).y, from.z))) {
                        result.add(new FakeStevePathSearch.Edge<>(next.immutable(),
                                column == 0 ? 1.3D : 1.0D, 1.0D));
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
            // 网格图没有真实碰撞体，可达性已经由 standable/canStep 表达，这里直接放行。
            if (grid != null) return true;
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
                if (!shape(below).isEmpty() || water(below) || climbable(below)) return true;
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
                VoxelShape shape = shape(pos);
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
