package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * 实体交互方块 - 普通版本（占满1格的方块）
 * 全透明，无碰撞箱，创造模式玩家右键可打开UI
 */
public class EntityInteractionBlock extends BaseEntityBlock implements TaskInstinctShowableInterface, SimpleWaterloggedBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public EntityInteractionBlock(Properties settings) {
        super(settings.noOcclusion().noCollission());
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof EntityInteractionBlockEntity interactionBlockEntity) {
            // 记录右键点击
            if (player instanceof ServerPlayer serverPlayer) {
                interactionBlockEntity.recordPlayerClick(serverPlayer, false); // false = 右键
            }
            // 只有创造模式玩家可以打开UI
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.isCreative()) {
                interactionBlockEntity.openUI(serverPlayer);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void attack(BlockState state, Level world, BlockPos pos, Player player) {
        if (!world.isClientSide) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof EntityInteractionBlockEntity interactionBlockEntity) {
                // 记录左键点击
                if (player instanceof ServerPlayer serverPlayer) {
                    interactionBlockEntity.recordPlayerClick(serverPlayer, true); // true = 左键
                }
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EntityInteractionBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, TMMBlockEntities.ENTITY_INTERACTION_BLOCK, EntityInteractionBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ===== 红石信号输入（类似 TrainLightBlock） =====
    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos,
            boolean notify) {
        if (!world.isClientSide) {
            boolean isPowered = world.hasNeighborSignal(pos);
            if (isPowered != state.getValue(POWERED)) {
                world.setBlock(pos, state.setValue(POWERED, isPowered), 2);
                // 同步到 BlockEntity
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof EntityInteractionBlockEntity entity) {
                    entity.setReceivedRedstoneSignal(isPowered);
                }
            }
        }
    }

    // ===== 红石信号输出（类似 RemoteRedstoneBlock 的 getSignal） =====
    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof EntityInteractionBlockEntity entity) {
            if (entity.isOutputtingRedstone()) {
                return entity.getRedstoneOutputStrength();
            }
        }
        return 0;
    }

    // 任务路标ID - 不用于MapScanner扫描识别（没什么用）
    public static final int TASK_MARKER_INSTINCT_ID = 13;

    @Override
    public int taskInstinctId() {
        // 返回默认值，由 BlockEntity 的自定义值覆盖
        return TASK_MARKER_INSTINCT_ID;
    }

    /**
     * 获取指定位置的自定义任务本能ID
     */
    public static int getCustomTaskInstinctId(Level level, BlockPos pos) {
        if (level == null)
            return TASK_MARKER_INSTINCT_ID;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EntityInteractionBlockEntity entity) {
            if (entity.isTaskMarker()) {
                return entity.getTaskInstinctId();
            }
        }
        return TASK_MARKER_INSTINCT_ID;
    }

    @Override
    public boolean shouldRenderTaskInstinct(BlockState state, BlockPos pos, Player player) {
        // 只有在BlockEntity中标记为任务路标时才渲染
        if (state.getBlock() instanceof EntityInteractionBlock) {
            Level level = player.level();
            if (level != null) {
                if (level.getBlockEntity(pos) instanceof EntityInteractionBlockEntity entity) {
                    if (!entity.isTaskMarker()) {
                        return false;
                    }
                    // 根据透视条件判断
                    EntityInteractionBlockEntity.TaskHighlightCondition condition = entity.getTaskHighlightCondition();
                    switch (condition) {
                        case NONE:
                            return false;
                        case ALWAYS:
                            return true;
                        case NORMAL_TASK: {
                            // 检查玩家是否有一般任务
                            var taskComponent = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.KEY.get(player);
                            return taskComponent != null && !taskComponent.tasks.isEmpty();
                        }
                        case CUSTOM_TASK: {
                            // 检查玩家是否有匹配的自定义任务
                            var taskComponent = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.KEY.get(player);
                            if (taskComponent == null)
                                return false;
                            String customTaskId = entity.getTaskHighlightCustomTaskId();
                            if (customTaskId == null || customTaskId.isEmpty())
                                return false;
                            return taskComponent.tasks.values().stream()
                                    .anyMatch(task -> customTaskId.equals(task.getCustomTaskId()));
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        // 返回配置的任务框颜色
        if (state.getBlock() instanceof EntityInteractionBlock) {
            Level level = player.level();
            if (level != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof EntityInteractionBlockEntity entity) {
                    int color = entity.getTaskMarkerColor();
                    return new Color(color);
                }
            }
        }
        return Color.WHITE;
    }
}
