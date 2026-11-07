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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link FakeSteveNavWorld} 的 {@code ServerLevel} 实现，等价于原版的 {@code NodeEvaluator}。
 *
 * <p>
 * 它只做两件事：把「双脚所在格 + 周围环境」评估成 {@link FakeStevePathType}，以及提供跳跃/台阶判断所需的精确站立高度。
 * <b>所有读取结果都在本次搜索内缓存</b>，因为 A* 会把同一个格子当成不同节点的邻居反复询问
 * （原版也是靠 {@code Node} 缓存复用同一批方块状态）。
 *
 * <p>
 * 缓存生命周期 = 一次 {@link FakeSteveNavigator#find} 调用，搜索结束后随实例一起被 GC，不会跨 tick 残留。
 */
final class FakeSteveLevelNavWorld implements FakeSteveNavWorld {

    private final ServerLevel level;
    private final Set<BlockPos> occupied;
    /**
     * 地图是否禁止游泳。
     *
     * <p>
     * 禁止时水面既不能作为路线、也不能作为落点：{@link FakeSteveNavigator#canStep} 会把
     * {@link FakeStevePathType#WATER} 直接拒绝，这里再把它评估成 {@link FakeStevePathType#BLOCKED}，
     * 两层都拦住「跳进水里」的路线，A* 只能去找陆路。
     */
    private final boolean swimmingBanned;
    private final Map<BlockPos, FakeStevePathType> types = new HashMap<>();
    private final Map<BlockPos, Double> standHeights = new HashMap<>();
    private final Map<BlockPos, Boolean> dropBesideCache = new HashMap<>();

    FakeSteveLevelNavWorld(ServerLevel level, ServerPlayer mover) {
        this(level, mover, FakeSteveNavRules.of(level, mover).swimmingBanned());
    }

    FakeSteveLevelNavWorld(ServerLevel level, ServerPlayer mover, boolean swimmingBanned) {
        this.level = level;
        this.occupied = occupiedByPlayers(level, mover);
        this.swimmingBanned = swimmingBanned;
    }

    @Override
    public FakeStevePathType nodeType(BlockPos feet) {
        FakeStevePathType cached = types.get(feet);
        if (cached != null) {
            return cached;
        }
        FakeStevePathType computed = evaluate(feet);
        types.put(feet, computed);
        return computed;
    }

    /**
     * 节点类型评估：先判定「这一格能不能让身体双脚站住」，再按原版的分档给出代价类型。
     *
     * <p>
     * 与原版的差异只有两处：
     * <ul>
     * <li>本模组没有铁轨/栅栏/蜂蜜块这类特殊方块，但小门可以被 AI 打开，
     * 所以关闭的小门按 {@link FakeStevePathType#WALKABLE_DOOR}（代价 0）处理，而不是原版的不可通行；</li>
     * <li>地图禁止游泳时，水面直接评估成 {@link FakeStevePathType#BLOCKED}。
     * 这不是保守，而是事实：落水会被 {@code SREGameWorldComponent#playerEnvironmentDetection}
     * 判 {@code DeathReasons.CANNOT_SWIM}，把它当成「代价 8 的可通行节点」等于给 A* 一条死路。</li>
     * </ul>
     */
    private FakeStevePathType evaluate(BlockPos feet) {
        if (!FakeSteveNavigator.standable(level, feet)) {
            return FakeStevePathType.BLOCKED;
        }
        BlockState feetState = level.getBlockState(feet);
        if (isWaterAt(feetState, level.getBlockState(feet.above()))) {
            return swimmingBanned ? FakeStevePathType.BLOCKED : FakeStevePathType.WATER;
        }
        if (isDangerous(feetState)) {
            return FakeStevePathType.DANGEROUS;
        }
        if (FakeSteveDoorAccess.isOpenablePassage(feetState)) {
            return FakeStevePathType.WALKABLE_DOOR;
        }
        return FakeStevePathType.WALKABLE;
    }

    private static boolean isWaterAt(BlockState feetState, BlockState headState) {
        return feetState.getFluidState().is(FluidTags.WATER)
                || headState.getFluidState().is(FluidTags.WATER);
    }

    /** 火、岩浆块、仙人掌、甜浆果丛、细雪：能过，但优先绕开。 */
    private static boolean isDangerous(BlockState state) {
        return state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW);
    }

    @Override
    public boolean standable(BlockPos feet) {
        return nodeType(feet).passable();
    }

    @Override
    public boolean blockedByBody(BlockPos feet) {
        return occupied.contains(feet);
    }

    @Override
    public boolean isWater(BlockPos feet) {
        return level.getBlockState(feet).getFluidState().is(FluidTags.WATER)
                || level.getBlockState(feet.above()).getFluidState().is(FluidTags.WATER);
    }

    /** 站立高度：半砖/楼梯要算上自身的碰撞高度，否则会把「上台阶」误判成「爬墙」。 */
    @Override
    public double standY(BlockPos feet) {
        Double cached = standHeights.get(feet);
        if (cached != null) {
            return cached;
        }
        double height = FakeSteveNavigator.standingSurfaceY(level, feet);
        standHeights.put(feet, height);
        return height;
    }

    /** 贴着悬空边缘的格子：路线能不走就不走，避免身体擦着栏杆掉下去。 */
    @Override
    public boolean dropBeside(BlockPos feet) {
        Boolean cached = dropBesideCache.get(feet);
        if (cached != null) {
            return cached;
        }
        boolean beside = false;
        for (Direction direction : FakeSteveNavigator.HORIZONTAL) {
            BlockPos side = feet.relative(direction);
            if (!level.getBlockState(side).getFluidState().is(FluidTags.WATER)
                    && !hasFloor(side) && !hasFloor(side.below())) {
                beside = true;
                break;
            }
        }
        dropBesideCache.put(feet, beside);
        return beside;
    }

    private boolean hasFloor(BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** 门与台阶必须留在路线上：门要停下来开，台阶要抬脚，简化掉会让身体撞门/卡台阶。 */
    @Override
    public boolean preserveNode(BlockPos feet) {
        return FakeSteveDoorAccess.isOpenablePassage(level.getBlockState(feet))
                || stepBlock(feet);
    }

    /** 楼梯 / 半砖：原版不按跳跃键也能踩上去，禁跳地图里依然可走。 */
    @Override
    public boolean stepBlock(BlockPos feet) {
        return FakeSteveNavigator.isStepBlock(level.getBlockState(feet));
    }

    /**
     * 斜向移动时的切角检查：斜穿的两条直角边至少要有一条「在这一层高度上能通过」。
     * 对应原版「对角中间的两个位置的方块高度不能高于当前节点和对角节点」。
     */
    @Override
    public boolean canCutCorner(BlockPos column, double limitY) {
        double ceiling = limitY + FakeStevePathPolicy.STEP_HEIGHT;
        for (int dy = 1; dy >= -1; dy--) {
            BlockPos feet = column.offset(0, dy, 0);
            if (standable(feet) && standY(feet) <= ceiling) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把任意位置吸附到「真正能站的那一格」：原版在建立路径前同样会重算起点，
     * 因为 AI 提交的坐标常常落在半空、门里或方块内部。
     */
    @Override
    public BlockPos normalize(BlockPos pos) {
        for (int dy : new int[] { 0, 1, -1 }) {
            BlockPos candidate = pos.offset(0, dy, 0);
            if (standable(candidate)) {
                return candidate.immutable();
            }
        }
        return pos.immutable();
    }

    /** 其他玩家（含被夺舍的身体）占住的格子：不允许穿过别人，目标格除外。 */
    private static Set<BlockPos> occupiedByPlayers(ServerLevel level, ServerPlayer mover) {
        Set<BlockPos> result = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player == mover || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            result.add(player.blockPosition().immutable());
        }
        return result;
    }
}
