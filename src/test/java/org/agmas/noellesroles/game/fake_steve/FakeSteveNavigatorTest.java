package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.api.AreasSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 当前生产 A* 内核与地图约束的网格单元测试。
 *
 * <p>不启动 Minecraft：手写网格提供水平邻接和站立高度，使用生产的
 * {@link FakeStevePathSearch} 与 {@link FakeStevePathPolicy} 检查路线和移动约束。
 * 真实方块碰撞评估仍需要 ServerLevel，不在本测试范围内。
 */
class FakeSteveNavigatorTest {

    // ---------------------------------------------------------------- 地图约束映射

    @Test
    void swimRequiresAllFourMapSettings() {
        AreasSettings areas = new AreasSettings();
        areas.canSwim = true;
        areas.canSimpleSwim = true;
        areas.canUnderWater = true;
        areas.allowInDeepWater = true;
        assertTrue(FakeSteveNavRules.of(false, areas, null).canSwim());

        areas.allowInDeepWater = false;
        assertFalse(FakeSteveNavRules.of(false, areas, null).canSwim(),
                "深水致死开启时不能把水面当路线");
        assertTrue(FakeSteveNavRules.of(false, areas, null).swimmingBanned());
    }

    @Test
    void missingAreaSettingsFallsBackToNoSwim() {
        FakeSteveNavRules rules = FakeSteveNavRules.of(true, null, null);
        assertTrue(rules.canJump());
        assertFalse(rules.canSwim());
        assertEquals(FakeSteveNavRules.DEFAULT_MAX_FALL_DISTANCE, rules.maxFallDistance());
    }

    @Test
    void fallToDeathHeightIsConvertedWithSafetyMargin() {
        // 0 表示地图没启用摔落致死，回退到兜底落差。
        assertEquals(3.0D, FakeSteveNavRules.maxFallDistance(0, null));
        // 1 格致死：连一格都下不去。
        assertEquals(0.0D, FakeSteveNavRules.maxFallDistance(1, null));
        // 2 格致死：允许 1 格。
        assertEquals(1.0D, FakeSteveNavRules.maxFallDistance(2, null));
        // 3 格致死：允许 2 格（3 格刚好踩线，必须拒绝）。
        assertEquals(2.0D, FakeSteveNavRules.maxFallDistance(3, null));
    }

    @Test
    void roleOverrideWinsOverMapHeight() {
        assertEquals(1.0D, FakeSteveNavRules.maxFallDistance(6, 2));
        assertEquals(3.0D, FakeSteveNavRules.maxFallDistance(6, 0));
    }

    // ---------------------------------------------------------------- 基本寻路

    @Test
    void straightCorridorKeepsWalkableWaypointsToGoal() {
        GridWorld world = new GridWorld();
        world.corridor(new BlockPos(0, 0, 0), new BlockPos(0, 0, 5));

        PathResult result = search(world, new BlockPos(0, 0, 0), new BlockPos(0, 0, 5),
                FakeSteveNavRules.PERMISSIVE);

        assertTrue(result.reached());
        ArrayDeque<BlockPos> path = result.path();
        assertEquals(5, path.size(), "当前搜索内核保留沿途路点：" + path);
        assertEquals(new BlockPos(0, 0, 5), path.peekLast(), "必须实际到达目标，而不是停在邻格");
        assertTrue(path.stream().allMatch(world::standable));
    }

    @Test
    void wallIsWalkedAroundInsteadOfThrough() {
        GridWorld world = new GridWorld();
        // 5×5 的开阔地面，中间一列 (x = 2) 在 z = 0..2 立墙，只有 z = 3 留缺口。
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 2 && z <= 2) {
                    continue;
                }
                world.open(new BlockPos(x, 0, z));
            }
        }

        PathResult result = search(world, new BlockPos(0, 0, 0), new BlockPos(4, 0, 0),
                FakeSteveNavRules.PERMISSIVE);

        assertTrue(result.reached(), "绕墙后应当能到达：" + result.path());
        // 当前内核保留路点，路线不得穿过墙体 (2,0,0)..(2,0,2)。
        for (int z = 0; z <= 2; z++) {
            assertFalse(result.path().contains(new BlockPos(2, 0, z)),
                    "不能穿过墙体：" + result.path());
        }
    }

    @Test
    void enclosedStartReportsNotReached() {
        GridWorld world = new GridWorld();
        world.open(new BlockPos(0, 0, 0));
        world.open(new BlockPos(5, 0, 0));

        PathResult result = search(world, new BlockPos(0, 0, 0), new BlockPos(5, 0, 0),
                FakeSteveNavRules.PERMISSIVE);

        assertFalse(result.reached(), "走不出去时必须返回未到达");
        assertTrue(result.path().isEmpty(), "一步都没走，路线应为空：" + result.path());
    }

    @Test
    void startEqualToGoalIsReachedImmediately() {
        GridWorld world = new GridWorld();
        world.open(new BlockPos(0, 0, 0));

        PathResult result = search(world, new BlockPos(0, 0, 0), new BlockPos(0, 0, 0),
                FakeSteveNavRules.PERMISSIVE);

        assertTrue(result.reached());
        assertTrue(result.path().isEmpty());
    }

    @Test
    void cornerCuttingIsRejectedWhenBothSidesAreSolid() {
        GridWorld world = new GridWorld();
        // 起点 (0,0,0)、终点 (1,0,1) 斜向相邻，但两个直角格 (1,0,0) 与 (0,0,1) 都是实心。
        // 唯一通路要绕一大圈：z 轴向下 → x 轴向左 → 再绕回来。
        world.open(new BlockPos(0, 0, 0));
        world.open(new BlockPos(0, 0, -1));
        world.open(new BlockPos(0, 0, -2));
        world.open(new BlockPos(1, 0, -2));
        world.open(new BlockPos(2, 0, -2));
        world.open(new BlockPos(2, 0, -1));
        world.open(new BlockPos(2, 0, 0));
        world.open(new BlockPos(2, 0, 1));
        world.open(new BlockPos(1, 0, 1));

        PathResult result = search(world, new BlockPos(0, 0, 0), new BlockPos(1, 0, 1),
                FakeSteveNavRules.PERMISSIVE);

        assertTrue(result.reached(), "绕远路依然能到达：" + result.path());
        assertTrue(result.path().size() >= 2, "必须绕行，而不是一步斜穿：" + result.path());
        assertFalse(result.path().contains(new BlockPos(0, 0, 1)),
                "不能穿过实心格：" + result.path());
    }

    // ---------------------------------------------------------------- 地图约束

    @Test
    void waterIsBlockedWhenSwimmingIsBanned() {
        GridWorld world = new GridWorld();
        world.open(new BlockPos(0, 0, 0));
        world.water(new BlockPos(1, 0, 0));
        world.water(new BlockPos(2, 0, 0));
        world.open(new BlockPos(3, 0, 0));

        PathResult banned = search(world, new BlockPos(0, 0, 0), new BlockPos(3, 0, 0),
                new FakeSteveNavRules(true, false, 3.0D));
        assertFalse(banned.reached(), "禁游地图不能跨水：" + banned.path());

        PathResult allowed = search(world, new BlockPos(0, 0, 0), new BlockPos(3, 0, 0),
                new FakeSteveNavRules(true, true, 3.0D));
        assertTrue(allowed.reached(), "允许游泳时水面就是可走的捷径：" + allowed.path());
    }

    @Test
    void stairsAreWalkableWithoutJumpButFullBlocksAreNot() {
        FakeSteveNavRules noJump = new FakeSteveNavRules(false, true, 3.0D);

        // 楼梯：抬升 1.0，但带台阶语义（原版自动抬脚），禁跳也应当可走。
        GridWorld stairs = new GridWorld();
        stairs.open(new BlockPos(0, 0, 0));
        stairs.step(new BlockPos(1, 0, 0));
        stairs.open(new BlockPos(2, 0, 0));
        assertTrue(search(stairs, new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), noJump).reached(),
                "禁跳地图里楼梯必须还能走");

        // 整格高台：抬升同样 1.0，但没有台阶语义，禁跳时上不去。
        GridWorld plateau = new GridWorld();
        plateau.open(new BlockPos(0, 0, 0));
        plateau.plateau(new BlockPos(1, 0, 0));
        plateau.open(new BlockPos(2, 0, 0));
        assertFalse(search(plateau, new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), noJump).reached(),
                "禁跳地图里不能跳上一整格高台");

        FakeSteveNavRules jumping = new FakeSteveNavRules(true, true, 3.0D);
        assertTrue(search(plateau, new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), jumping).reached(),
                "允许跳跃时一格高台可以上去");
    }

    @Test
    void fallLimitFollowsMapDeathHeight() {
        GridWorld world = new GridWorld();
        // 从 y = 1 的平台走到 y = 0 的地面：一格落差。
        world.open(new BlockPos(0, 1, 0));
        world.open(new BlockPos(1, 0, 0));
        world.open(new BlockPos(2, 0, 0));

        assertTrue(search(world, new BlockPos(0, 1, 0), new BlockPos(2, 0, 0),
                new FakeSteveNavRules(true, true, 1.0D)).reached(),
                "落差上限 1 格时一格下落允许");

        assertFalse(search(world, new BlockPos(0, 1, 0), new BlockPos(2, 0, 0),
                new FakeSteveNavRules(true, true, 0.0D)).reached(),
                "落差上限 0 格时一格下落也必须拒绝");
    }

    // ---------------------------------------------------------------- 工具

    private static PathResult search(GridWorld world, BlockPos start, BlockPos goal,
                                     FakeSteveNavRules rules) {
        var result = FakeStevePathSearch.find(start, goal::equals,
                pos -> Math.max(Math.max(Math.abs(pos.getX() - goal.getX()), Math.abs(pos.getZ() - goal.getZ())),
                        Math.abs(pos.getY() - goal.getY()) / 3.0D),
                pos -> neighbours(world, pos, rules), 512);
        return new PathResult(new ArrayDeque<>(result.path()), result.complete());
    }

    /** 与当前导航一样只生成水平邻接，垂直偏移由网格高度及纯移动策略约束。 */
    private static List<FakeStevePathSearch.Edge<BlockPos>> neighbours(GridWorld world, BlockPos from,
            FakeSteveNavRules rules) {
        List<FakeStevePathSearch.Edge<BlockPos>> edges = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos column = from.relative(direction);
            for (int dy : new int[] {0, 1, -1, -2, -3}) {
                BlockPos next = column.offset(0, dy, 0);
                if (!world.standable(next) || world.isWater(next) && !rules.canSwim()) continue;
                double rise = world.standY(next) - world.standY(from);
                if (rise > 0 && !FakeStevePathPolicy.canAscendWithoutJump(rise, world.stepBlock(next))
                        && (!rules.canJump() || rise > 1.25D)) continue;
                if (-rise > rules.maxFallDistance()) continue;
                edges.add(new FakeStevePathSearch.Edge<>(next, 1.0D + Math.abs(rise) * 0.55D));
                break;
            }
        }
        return edges;
    }

    private record PathResult(ArrayDeque<BlockPos> path, boolean reached) {
    }

    /**
     * 手写网格世界：<b>默认全部实心</b>，只有显式 {@code open} / {@code water} / {@code step}
     * 过的格子才存在。这与真实世界一致——空气不是地面，不挖通道就没有路。
     */
    private static final class GridWorld implements FakeSteveNavWorld {
        private final Set<BlockPos> open = new HashSet<>();
        private final Set<BlockPos> water = new HashSet<>();
        private final Set<BlockPos> steps = new HashSet<>();
        private final Map<BlockPos, Double> heights = new HashMap<>();

        void open(BlockPos pos) {
            open.add(pos.immutable());
        }

        /** 沿一条轴向直线打通所有格子（含两端）。 */
        void corridor(BlockPos from, BlockPos to) {
            int dx = Integer.signum(to.getX() - from.getX());
            int dy = Integer.signum(to.getY() - from.getY());
            int dz = Integer.signum(to.getZ() - from.getZ());
            BlockPos cursor = from;
            open(cursor);
            while (!cursor.equals(to)) {
                cursor = cursor.offset(dx, dy, dz);
                open(cursor);
            }
        }

        void water(BlockPos pos) {
            water.add(pos.immutable());
        }

        /** 楼梯/半砖：站立高度抬升 1.0，且带台阶语义。 */
        void step(BlockPos pos) {
            steps.add(pos.immutable());
            heights.put(pos.immutable(), pos.getY() + 1.0D);
        }

        /**
         * 整格高台：该格本身可以站（是实心方块的顶面），站立高度比同层高 1.0，
         * 但没有台阶语义——必须跳跃才能上去。
         */
        void plateau(BlockPos pos) {
            open.add(pos.immutable());
            heights.put(pos.immutable(), pos.getY() + 1.0D);
        }

        @Override
        public FakeStevePathType nodeType(BlockPos feet) {
            if (water.contains(feet)) {
                return FakeStevePathType.WATER;
            }
            if (open.contains(feet) || steps.contains(feet)) {
                return FakeStevePathType.WALKABLE;
            }
            return FakeStevePathType.BLOCKED;
        }

        @Override
        public boolean blockedByBody(BlockPos feet) {
            return false;
        }

        @Override
        public boolean isWater(BlockPos feet) {
            return water.contains(feet);
        }

        @Override
        public double standY(BlockPos feet) {
            return heights.getOrDefault(feet, (double) feet.getY());
        }

        @Override
        public boolean dropBeside(BlockPos feet) {
            return false;
        }

        @Override
        public boolean stepBlock(BlockPos feet) {
            return steps.contains(feet);
        }

        @Override
        public boolean preserveNode(BlockPos feet) {
            return steps.contains(feet);
        }

        @Override
        public boolean canCutCorner(BlockPos column, double limitY) {
            return nodeType(column).passable();
        }

        @Override
        public BlockPos normalize(BlockPos pos) {
            for (int dy : new int[] { 0, 1, -1 }) {
                BlockPos candidate = pos.offset(0, dy, 0);
                if (nodeType(candidate).passable()) {
                    return candidate.immutable();
                }
            }
            return pos.immutable();
        }
    }
}
