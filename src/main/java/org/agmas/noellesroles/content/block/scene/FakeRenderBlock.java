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

package org.agmas.noellesroles.content.block.scene;

import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * 假方块：只渲染无实体（不发光）
 * 红石触发时会连带周围相连的相同方块一起改变 LIT 状态（距离 ≤ 16）
 */
public class FakeRenderBlock extends BreakingBridgeBlock {

    public static final MapCodec<FakeRenderBlock> CODEC = simpleCodec(FakeRenderBlock::new);
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    // 防止递归传播的标志（每个线程独立）
    private static final ThreadLocal<Boolean> PROPAGATING = ThreadLocal.withInitial(() -> false);

    @Override
    public MapCodec<? extends FakeRenderBlock> codec() {
        return CODEC;
    }

    public FakeRenderBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState().setValue(TYPE, SlabType.DOUBLE).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
        return super.getStateForPlacement(blockPlaceContext).setValue(LIT,
                blockPlaceContext.getLevel().hasNeighborSignal(blockPlaceContext.getClickedPos()));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc) {
            if (ecc.getEntity() instanceof Player player) {
                if (player.isCreative()) {
                    return Shapes.block();
                }
            }
        }
        if (state.getValue(LIT)) {
            if (recursionLock.get()) {
                return Shapes.block();
            }
            recursionLock.set(true);
            try {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                    if (bbbe.displayState != null) {
                        var t = bbbe.displayState.getShape(world, pos, context);
                        return t;
                    }
                }
            } finally {
                recursionLock.set(false);
            }
            return Shapes.block();
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(LIT)) {
            if (recursionLock.get()) {
                return Shapes.block();
            }
            recursionLock.set(true);
            try {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                    if (bbbe.displayState != null) {
                        var t = bbbe.displayState.getCollisionShape(world, pos, context);
                        return t;
                    }
                }
                return Shapes.block();
            } finally {
                recursionLock.set(false);
            }
        }
        return Shapes.empty();
    }

    @Override
    protected void neighborChanged(BlockState blockState, Level level, BlockPos blockPos, Block block,
            BlockPos blockPos2, boolean bl) {
        if (level.isClientSide)
            return;
        if (PROPAGATING.get())
            return;

        boolean hasSignal = level.hasNeighborSignal(blockPos);
        boolean currentLit = blockState.getValue(LIT);
        if (currentLit == hasSignal) {
            return;
        }

        PROPAGATING.set(true);
        try {
            int maxDist = 16;
            Queue<BlockPos> posQueue = new ArrayDeque<>();
            Queue<Integer> distQueue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();

            posQueue.add(blockPos);
            distQueue.add(0);
            visited.add(blockPos);

            while (!posQueue.isEmpty()) {
                BlockPos cur = posQueue.poll();
                int dist = distQueue.poll();

                BlockState curState = level.getBlockState(cur);
                if (curState.getBlock() instanceof FakeRenderBlock) {
                    // 关键修改：使用 Block.UPDATE_CLIENTS，避免触发邻居更新
                    boolean curHasSignal = level.hasNeighborSignal(cur);
                    if (curHasSignal && !hasSignal) {
                        hasSignal = curHasSignal;
                    }
                    if (curState.getValue(LIT) != hasSignal)
                        level.setBlock(cur, curState.setValue(LIT, hasSignal), Block.UPDATE_CLIENTS);
                }

                if (dist >= maxDist)
                    continue;

                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = cur.relative(direction);
                    if (visited.contains(neighbor))
                        continue;
                    BlockState neighborState = level.getBlockState(neighbor);
                    if (neighborState.getBlock() instanceof FakeRenderBlock) {
                        visited.add(neighbor);
                        posQueue.add(neighbor);
                        distQueue.add(dist + 1);
                    }
                }
            }
        } finally {
            PROPAGATING.set(false);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        // 空实现，不触发断裂
    }

    @Override
    public void startBreaking(BlockState state, ServerLevel level, BlockPos pos) {
        // 空实现
    }

    @Override
    public void startRecovery(BlockState state, ServerLevel level, BlockPos pos, int time) {
        // 空实现
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 原有延迟切换逻辑（保留，但通常不会被调用）
        if (state.getValue(LIT) && !level.hasNeighborSignal(pos)) {
            level.setBlock(pos, state.cycle(LIT), 2);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BreakingBridgeBlockEntity) {
                level.removeBlockEntity(pos);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreakingBridgeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState,
            BlockEntityType<T> blockEntityType) {
        return null; // 无需 tick
    }
}