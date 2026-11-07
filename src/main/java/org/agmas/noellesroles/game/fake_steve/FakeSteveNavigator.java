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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 被夺舍身体的寻路器：<b>有界 A*</b>，语义对齐原版 {@code PathFinder} + {@code WalkNodeEvaluator}。
 *
 * <h2>为什么重写</h2>
 * 旧实现有几个结构性问题，单点修补无法解决：
 * <ul>
 * <li><b>代价只有「1 格 = 1」</b>：水面、危险方块、贴边缘的格子都靠三个魔法常数在循环里临时加，
 * 一旦某个方向被忽略（例如没有 {@code dy} 惩罚），A* 就会给出穿墙抄近路的路线；</li>
 * <li><b>没有切角检查</b>：斜向邻居只要两个直角格各自「能站」就放行，等于允许身体从墙角斜穿过去，
 * 于是路线看起来总是卡在门口和墙角；</li>
 * <li><b>相邻节点的选取有副作用</b>：每个方向对 {@code dy} 从 0 往上找第一个能站的格子，
 * 允许「跳到墙顶再走」这种玩家做不到的路线；</li>
 * <li><b>路径没有简化</b>：每个格子都是一个路点，身体每格都要修正朝向，走起来左右抖。</li>
 * </ul>
 *
 * <h2>现在的做法</h2>
 * <ol>
 * <li>把世界读成 {@link FakeStevePathType}（等价 {@code NodeEvaluator}），见 {@link FakeSteveLevelNavWorld}；</li>
 * <li>A* 的 {@code g} 累加「移动距离 + 节点代价 + 跳跃/落差/贴边惩罚」，
 * {@code h} 用欧几里得距离（可采纳，保证一致性，比原版 1.5 倍更慢但更稳）；</li>
 * <li>每个水平方向只取<b>一个</b>最合适的 {@code dy}：先看同层，再看上一层（受跳跃能力限制），最后看下一层
 * （受最大落差限制）；</li>
 * <li>斜向移动必须通过「切角检查」，见 {@link FakeSteveNavWorld#canCutCorner}；</li>
 * <li>搜索结束后做一次<b>共线合并</b>，把同一条直线上的中间节点删掉，只保留拐点、门与台阶。</li>
 * </ol>
 *
 * <p>
 * 搜索本身不碰 {@code ServerLevel}，因此可以用一张网格图做单元测试（见
 * {@code FakeSteveNavigatorTest}）。
 */
final class FakeSteveNavigator {
    /** 单次搜索的节点预算上限。原版用「最大距离 × 每格最大访问节点数」，这里直接给固定上界。 */
    static final int MAX_VISITED = 1024;
    static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    /** 地图没有配置摔落致死高度时的兜底落差（格）。真正的值由 {@link FakeSteveNavRules} 决定。 */
    static final double MAX_FALL_DISTANCE = FakeSteveNavRules.DEFAULT_MAX_FALL_DISTANCE;
    /** 跳跃可以到达的最大抬升（格）：玩家跳跃上限约 1.25 格。 */
    static final double MAX_JUMP_RISE = 1.25D;

    private FakeSteveNavigator() {
    }

    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal) {
        return find(level, mover, goal, false);
    }

    /**
     * 从身体当前位置寻路到 {@code goal}。
     *
     * @param explicitTarget 追击/潜行/同化这类「明确目标」会放宽节点预算，并允许走直线走廊
     * @return 路点队列（第一个是下一个要走到的格子）；无路可走时返回空队列
     */
    static FakeSteveNavigator.PathResult search(ServerLevel level, ServerPlayer mover,
            BlockPos goal, boolean explicitTarget) {
        FakeSteveNavRules rules = FakeSteveNavRules.of(level, mover);
        // 世界评估层与搜索层共用同一份规则：禁游时水面在评估阶段就被判为 BLOCKED，
        // 不会先按「代价 8 的可通行节点」进开放集、再在 canStep 里被拒（浪费节点预算）。
        FakeSteveLevelNavWorld world =
                new FakeSteveLevelNavWorld(level, mover, rules.swimmingBanned());
        Search search = new Search(world, rules,
                explicitTarget ? MAX_VISITED * 2 : MAX_VISITED);
        return search.find(mover.blockPosition(), goal, explicitTarget);
    }

    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal,
                                     boolean explicitTarget) {
        return search(level, mover, goal, explicitTarget).path();
    }

    static boolean reaches(ArrayDeque<BlockPos> path, BlockPos goal) {
        BlockPos last = path.peekLast();
        return last != null && manhattan(last, goal) <= 1;
    }

    /**
     * 一次 A* 搜索。持有本次搜索的全部状态（开放集、来源表、代价表、方块缓存），
     * 搜索结束后整体丢弃，不会跨 tick 残留。
     */
    static final class Search {
        /** 斜向移动相对直线的额外惩罚：让路线更愿意走直线，减少左右抖动。 */
        private static final double DIAGONAL_PENALTY = 0.10D;
        /** 每抬升一格（跳或上台阶）的额外代价。 */
        private static final double RISE_COST = 1.6D;
        /** 每下落一格的额外代价：下落不是不能走，但能不走就不走。 */
        private static final double DROP_COST = 0.6D;
        /** 贴着悬空边缘的节点代价，对应原版的「危险边缘」。 */
        private static final double EDGE_COST = 2.0D;
        /** 切角检查失败时视为该斜向不可通行，不需要额外代价。 */

        private final FakeSteveNavWorld world;
        private final FakeSteveNavRules rules;
        private final int visitBudget;

        private final PriorityQueue<Node> open = new PriorityQueue<>();
        private final Map<BlockPos, Double> bestCost = new HashMap<>();
        private final Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        private final Set<BlockPos> closed = new HashSet<>();
        private final List<Step> steps = new ArrayList<>(16);
        private BlockPos goal = BlockPos.ZERO;
        private long sequence;

        Search(FakeSteveNavWorld world, FakeSteveNavRules rules, int visitBudget) {
            this.world = world;
            this.rules = rules == null ? FakeSteveNavRules.PERMISSIVE : rules;
            this.visitBudget = Math.max(1, visitBudget);
        }
        /**
         * @param start          身体当前所在格
         * @param requestedGoal  AI 提交的目标格
         * @param explicitTarget 是否明确目标（影响「能否直接走直线」的快速通道）
         */
        PathResult find(BlockPos start, BlockPos requestedGoal, boolean explicitTarget) {
            BlockPos normalizedStart = world.normalize(start);
            BlockPos normalizedGoal = world.normalize(requestedGoal);
            this.goal = normalizedGoal;
            if (normalizedStart.equals(normalizedGoal)) {
                return new PathResult(new ArrayDeque<>(), true, 0);
            }
            if (FakeStevePathPolicy.shouldPreferDirectRoute(explicitTarget,
                    corridorClear(normalizedStart, normalizedGoal))) {
                ArrayDeque<BlockPos> direct = new ArrayDeque<>(1);
                direct.add(normalizedGoal);
                return new PathResult(direct, true, 0);
            }

            bestCost.put(normalizedStart, 0.0D);
            open.add(new Node(normalizedStart, heuristic(normalizedStart), 0.0D, sequence++));

            BlockPos reached = null;
            BlockPos bestPartial = normalizedStart;
            int bestPartialDistance = manhattan(normalizedStart, normalizedGoal);
            int visited = 0;

            while (!open.isEmpty() && visited < visitBudget) {
                Node current = open.poll();
                if (!closed.add(current.pos())) {
                    continue;
                }
                visited++;
                int remaining = manhattan(current.pos(), normalizedGoal);
                if (remaining <= 1) {
                    reached = current.pos();
                    break;
                }
                if (remaining < bestPartialDistance) {
                    bestPartial = current.pos();
                    bestPartialDistance = remaining;
                }
                collectNeighbours(current.pos());
                for (Step step : steps) {
                    if (closed.contains(step.pos())) {
                        continue;
                    }
                    double tentative = current.cost() + step.cost();
                    if (tentative >= bestCost.getOrDefault(step.pos(), Double.MAX_VALUE)) {
                        continue;
                    }
                    bestCost.put(step.pos(), tentative);
                    cameFrom.put(step.pos(), current.pos());
                    open.add(new Node(step.pos(), tentative + heuristic(step.pos()),
                            tentative, sequence++));
                }
            }

            if (reached == null) {
                return new PathResult(build(bestPartial), false, visited);
            }
            return new PathResult(build(reached), true, visited);
        }

        /**
         * 收集当前节点的所有后继。
         *
         * <p>
         * 同层优先，其次上一层（需要跳跃能力，且不超过跳跃高度），最后下一层（不超过最大落差）。
         * 这样既不会出现「跳到墙顶」这种玩家做不到的路线，也不会让同一方向产生三个互相竞争的节点。
         *
         * <p>
         * 地图禁止游泳时，水中的竖直移动也一并取消：此时身体一旦入水就会被环境判死，
         * 唯一正确的做法是让 {@link #canStep} 直接拒绝水面，而不是提供「上浮逃出」的假出路。
         */
        private void collectNeighbours(BlockPos current) {
            steps.clear();
            for (Direction direction : HORIZONTAL) {
                addHorizontal(current, current.relative(direction), 1.0D);
            }
            addDiagonal(current, Direction.NORTH, Direction.EAST);
            addDiagonal(current, Direction.EAST, Direction.SOUTH);
            addDiagonal(current, Direction.SOUTH, Direction.WEST);
            addDiagonal(current, Direction.WEST, Direction.NORTH);
            if (rules.canSwim() && world.isWater(current)) {
                // 游泳时允许竖直移动：水面上下浮动是唯一能离开水体的方式。
                addVertical(current, current.above());
                addVertical(current, current.below());
            }
        }

        private void addHorizontal(BlockPos current, BlockPos column, double distance) {
            BlockPos candidate = pickVertical(current, column);
            if (candidate != null) {
                steps.add(new Step(candidate, stepCost(current, candidate, distance)));
            }
        }

        private void addDiagonal(BlockPos current, Direction first, Direction second) {
            BlockPos sideA = current.relative(first);
            BlockPos sideB = current.relative(second);
            double limitY = Math.max(world.standY(current), world.standY(current.relative(first).relative(second)));
            if (!world.canCutCorner(sideA, limitY) || !world.canCutCorner(sideB, limitY)) {
                return;
            }
            BlockPos column = current.relative(first).relative(second);
            BlockPos candidate = pickVertical(current, column);
            if (candidate != null) {
                steps.add(new Step(candidate,
                        stepCost(current, candidate, Math.sqrt(2.0D)) + DIAGONAL_PENALTY));
            }
        }

        private void addVertical(BlockPos current, BlockPos candidate) {
            if (canStep(current, candidate) && !world.blockedByBody(candidate)) {
                steps.add(new Step(candidate, stepCost(current, candidate, 1.0D)));
            }
        }

        /**
         * 在同一水平坐标上挑一个能落脚的高度：同层 → 上一层 → 下一层。
         *
         * <p>
         * 只取第一个通过检查的高度，正是为了避免「上层够得着就优先跳上去」的副作用；
         * 也保证 {@code stepSafe} 在运行时不会遇到「路径说能走、实际被台阶挡住」的情况。
         */
        private BlockPos pickVertical(BlockPos current, BlockPos column) {
            for (int dy : FakeStevePathPolicy.VERTICAL_ORDER) {
                BlockPos candidate = column.offset(0, dy, 0);
                if (canStep(current, candidate) && !blockedUnlessGoal(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private boolean blockedUnlessGoal(BlockPos candidate) {
            return world.blockedByBody(candidate) && manhattan(candidate, goal) > 1;
        }

        /**
         * 一步是否可以走：类型可通过、抬升不超过跳跃能力、落差不超过地图允许的最大落差。
         *
         * <p>
         * 三类地图约束都在这里落地，因为它们全都是「这一步能不能走」的问题：
         * <ul>
         * <li><b>禁跳</b>：抬升上限从 1.25 格降到 {@link FakeStevePathPolicy#STEP_HEIGHT}，
         * 于是楼梯、半砖、耕地这类「原版自动抬脚」的方块仍然可走（玩家不用按跳跃键就能上去），
         * 而整格高台必须绕路——这与禁跳地图里真人的可行走范围完全一致；</li>
         * <li><b>禁游</b>：水面节点直接拒绝，A* 会去找陆路；找不到就老老实实返回「没路」，
         * 交给 {@code FakeSteveAi} 退避，而不是把身体送进必死的水里；</li>
         * <li><b>落差</b>：上限取地图 {@code fallToDeathHeight} 折算值，未配置时回退 3 格。</li>
         * </ul>
         */
        private boolean canStep(BlockPos from, BlockPos to) {
            FakeStevePathType type = world.nodeType(to);
            if (!type.passable()) {
                return false;
            }
            boolean water = world.isWater(to);
            if (water && rules.swimmingBanned()) {
                return false;
            }
            double rise = world.standY(to) - world.standY(from);
            if (rise > 0.0D) {
                if (rise > MAX_JUMP_RISE + 1.0E-4D) {
                    return false;
                }
                if (rules.canJump()) {
                    return true;
                }
                // 禁跳：只保留「原版不需要跳跃输入就能上去」的高度。
                return FakeStevePathPolicy.canAscendWithoutJump(rise, world.stepBlock(to));
            }
            if (rise < 0.0D) {
                // 水里下落不算坠落（只有允许游泳时才会走到这里）；陆地上超过最大落差就不走。
                return water || -rise <= rules.maxFallDistance() + 1.0E-4D;
            }
            return true;
        }


        private double stepCost(BlockPos from, BlockPos to, double distance) {
            double cost = distance + Math.max(0.0D, world.nodeType(to).cost());
            double rise = world.standY(to) - world.standY(from);
            if (rise > 0.0D) {
                cost += RISE_COST * Math.max(1.0D, rise);
            } else if (rise < 0.0D) {
                cost += DROP_COST * -rise;
            }
            if (world.dropBeside(to)) {
                cost += EDGE_COST;
            }
            return cost;
        }

        /** 欧几里得启发式：可采纳且一致，保证找到的路线在「代价」意义上最优。 */
        private double heuristic(BlockPos pos) {
            double dx = goal.getX() - pos.getX();
            double dy = goal.getY() - pos.getY();
            double dz = goal.getZ() - pos.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /**
         * 回溯并简化：只保留拐点、门与台阶。
         *
         * <p>
         * 返回的队列<b>不含起点</b>：身体已经在起点上，把起点留在队首只会让运行时多一次无意义的推进。
         */
        private ArrayDeque<BlockPos> build(BlockPos end) {
            List<BlockPos> reversed = new ArrayList<>();
            BlockPos cursor = end;
            while (cursor != null) {
                reversed.add(cursor);
                cursor = cameFrom.get(cursor);
            }
            Collections.reverse(reversed);
            ArrayDeque<BlockPos> simplified = simplify(reversed);
            // simplify 一定保留了起点（reversed 的第 0 个），这里去掉。
            if (!simplified.isEmpty()) {
                simplified.removeFirst();
            }
            return simplified;
        }

        /**
         * 共线合并：三点在同一条直线上时删掉中间那个。
         *
         * <p>
         * 只做「共线」判断，不做跨格可达性判定——中间点本身已经由 A* 证明可走，
         * 同一直线上的相邻三点之间不存在需要绕开的障碍。门与台阶一律保留，
         * 因为身体需要在门口停下开门、在台阶前抬脚。
         */
        private ArrayDeque<BlockPos> simplify(List<BlockPos> nodes) {
            if (nodes.size() <= 2) {
                return new ArrayDeque<>(nodes);
            }
            List<BlockPos> kept = new ArrayList<>(nodes.size());
            kept.add(nodes.get(0));
            for (int i = 1; i < nodes.size() - 1; i++) {
                BlockPos previous = kept.get(kept.size() - 1);
                BlockPos current = nodes.get(i);
                BlockPos next = nodes.get(i + 1);
                if (collinear(previous, current, next) && !world.preserveNode(current)) {
                    continue;
                }
                kept.add(current);
            }
            kept.add(nodes.get(nodes.size() - 1));
            ArrayDeque<BlockPos> result = new ArrayDeque<>(kept.size());
            for (BlockPos pos : kept) {
                result.addLast(pos.immutable());
            }
            return result;
        }

        /**
         * 三点是否共线且方向一致。
         *
         * <p>
         * 必须用叉积判断<b>方向</b>，而不是比较两段位移是否相等：简化过程中前一个保留点可能距离很远
         * （中间的点已经被删掉），此时「步长相同」永远不成立，整条直线就简化不掉。
         * 点积 &gt; 0 用来排除「原路折返」（夹角 180°）这种同样叉积为零的情况。
         */
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
            if (crossX != 0 || crossY != 0 || crossZ != 0) {
                return false;
            }
            return abx * bcx + aby * bcy + abz * bcz > 0;
        }

        /**
         * 直线走廊快速通道：起点与目标同高且中间每一格都能站时，直接给一个目标节点。
         * 这不是寻路，只是省掉一次注定得到「一条直线」的搜索（原版也有类似的直达优化）。
         *
         * <p>
         * 这条捷径必须与 {@link #canStep} 用同一套地图约束：否则禁游地图里「目标在水面另一侧、
         * 中间全是水」会被当成走廊直达，直接跳过所有安全检查。
         */
        private boolean corridorClear(BlockPos start, BlockPos goal) {
            if (start.getY() != goal.getY()) {
                return false;
            }
            int dx = goal.getX() - start.getX();
            int dz = goal.getZ() - start.getZ();
            int length = Math.max(Math.abs(dx), Math.abs(dz));
            if (length == 0) {
                return true;
            }
            int stepX = Integer.signum(dx);
            int stepZ = Integer.signum(dz);
            BlockPos previous = start;
            for (int i = 1; i <= length; i++) {
                BlockPos pos = start.offset(stepX * i, 0, stepZ * i);
                if (!world.standable(pos) || world.blockedByBody(pos) && manhattan(pos, goal) > 1) {
                    return false;
                }
                // 同层走廊理论上 rise 恒为 0，但仍按 canStep 复核：水面、危险方块、落差都要过一遍。
                if (!canStep(previous, pos)) {
                    return false;
                }
                previous = pos;
            }
            return true;
        }
    }

    /**
     * 一次搜索的结果。
     *
     * @param path    简化后的路点（不含起点）
     * @param reached 是否真的到达了目标；false 时 path 是「离目标最近的一段」
     * @param visited 本次展开的节点数，用于调参和日志
     */
    record PathResult(ArrayDeque<BlockPos> path, boolean reached, int visited) {
    }

    private record Node(BlockPos pos, double estimate, double cost, long sequence)
            implements Comparable<Node> {
        @Override
        public int compareTo(Node other) {
            int byEstimate = Double.compare(estimate, other.estimate);
            if (byEstimate != 0) {
                return byEstimate;
            }
            // 同估值时优先代价更小、再按入队顺序，保证同一局面下路线稳定（不会每 tick 变一次）。
            int byCost = Double.compare(cost, other.cost);
            return byCost != 0 ? byCost : Long.compare(sequence, other.sequence);
        }
    }

    private record Step(BlockPos pos, double cost) {
    }

    static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    /**
     * 台阶/楼梯：可以直接踩上去的方块，不需要跳跃输入。
     * 地毯与活板门<b>不算</b>：它们本身不是可靠的地面。
     */
    static boolean isStepBlock(BlockState state) {
        return state.getBlock() instanceof StairBlock
                || state.getBlock() instanceof SlabBlock;
    }

    static boolean safeStand(ServerLevel level, BlockPos pos) {
        for (int dy : new int[] { 0, 1, -1 }) {
            if (standable(level, pos.offset(0, dy, 0))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 防坠落判定：只有「下方足够近的地方都没有地面」才拒绝。一格台阶向下是正常行走，绝不能因此把身体冻住。
     *
     * <p>
     * 两处必须服从地图配置，否则寻路算出的安全路线会被这个运行时守卫直接否掉（表现为身体原地不动）：
     * <ul>
     * <li><b>禁游</b>：前方是流体时不再无条件返回 true。禁游地图里踏进水面就是
     * {@code DeathReasons.CANNOT_SWIM}，必须当成不安全；</li>
     * <li><b>落差</b>：向下探测的深度取地图允许的最大落差，而不是固定 3 格。
     * 地图把致死高度设成 2 格时，3 格深坑不再是「安全」。</li>
     * </ul>
     */
    static boolean stepSafe(ServerLevel level, Vec3 from, Vec3 horizontal) {
        return stepSafe(level, from, horizontal, FakeSteveNavRules.of(level));
    }

    static boolean stepSafe(ServerLevel level, Vec3 from, Vec3 horizontal,
                            FakeSteveNavRules rules) {
        Vec3 ahead = from.add(horizontal);
        BlockPos feet = BlockPos.containing(ahead);
        if (!level.getBlockState(feet).getFluidState().isEmpty()) {
            // 水面：只有允许游泳时才算安全落点。
            return rules.canSwim();
        }
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return true;
        }
        // 向下探测到「允许落差」为止：落在这个深度内的地面都还能站住。
        int depth = Math.max(1, (int) Math.ceil(rules.maxFallDistance())) + 1;
        for (int dy = 0; dy <= depth; dy++) {
            BlockPos below = feet.below(dy);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 正前方是否有实体障碍（墙、关着的门、别人放的方块）。
     *
     * <p>
     * 用途只有一个：轨迹巡逻时把前进力度放轻，别顶着方块空转。它<b>不是</b>路径可行性判断。
     * 注意 {@link #stepSafe} 是「防坠落」而不是「防撞墙」：前方是实心方块时它反而返回 true。
     * 台阶、半砖、可以打开的门都不算墙。
     */
    static boolean wallAhead(ServerLevel level, Vec3 from, Vec3 horizontal) {
        BlockPos ahead = BlockPos.containing(from.add(horizontal));
        BlockState state = level.getBlockState(ahead);
        VoxelShape shape = state.getCollisionShape(level, ahead);
        if (shape.isEmpty()) {
            return false;
        }
        if (isStepBlock(state) || FakeSteveDoorAccess.isOpenablePassage(state)) {
            return false;
        }
        // 低于 0.6 格的矮碰撞体原版可以直接踩上去，不算墙。
        return shape.max(Direction.Axis.Y) >= 0.6D;
    }

    /**
     * 身体双脚正好站在 {@code feet} 这一格是否可行。
     *
     * <p>
     * 包内可见：{@link FakeSteveTrailRecorder} 用它把玩家的实时位置投影成「真正能站的路点」。
     */
    static boolean standable(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        VoxelShape feetShape = feetState.getCollisionShape(level, feet);
        boolean feetShapeEmpty = feetShape.isEmpty();
        double feetCollisionHeight = feetShapeEmpty ? 0.0D : feetShape.max(Direction.Axis.Y);
        boolean feetFree = FakeStevePathPolicy.feetCanOccupy(
                feetShapeEmpty, feetCollisionHeight, isStepBlock(feetState),
                feetState.getBlock() instanceof SlabBlock,
                FakeSteveDoorAccess.isOpenablePassage(feetState));
        boolean headFree = FakeStevePathPolicy.headCanOccupy(
                headState.getCollisionShape(level, feet.above()).isEmpty(),
                isStepBlock(headState), headState.getBlock() instanceof SlabBlock,
                FakeSteveDoorAccess.isOpenablePassage(headState));
        boolean swimming = feetState.getFluidState().is(FluidTags.WATER)
                || headState.getFluidState().is(FluidTags.WATER);
        boolean groundBelow = !level.getBlockState(feet.below())
                .getCollisionShape(level, feet.below()).isEmpty();
        // 半砖或楼梯本身就是地面；地毯永远不是。
        boolean selfSupporting = !feetShapeEmpty && feetCollisionHeight >= 0.4D;
        return feetFree && headFree && (swimming || groundBelow || selfSupporting);
    }

    /** 站在该格时双脚的世界坐标高度（含半砖/楼梯的精确高度）。 */
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
}
