package io.wifi.starrailexpress.content.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 实体交互方块 - 镶板版本（只有一个面，可以贴在其它方块上）
 * 全透明，无碰撞箱，创造模式玩家右键可打开UI
 * 参考屏障镶板的设计
 */
public class EntityInteractionPanelBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape UP_SHAPE = box(0.0, 15.9, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape DOWN_SHAPE = box(0.0, 0.0, 0.0, 16.0, 0.1, 16.0);
    private static final VoxelShape EAST_SHAPE = box(0.0, 0.0, 0.0, 0.1, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = box(15.9, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 0.1);
    private static final VoxelShape NORTH_SHAPE = box(0.0, 0.0, 15.9, 16.0, 16.0, 16.0);
    private static final Map<Direction, VoxelShape> SHAPES_FOR_DIRECTIONS = Util.make(Maps.newEnumMap(Direction.class),
            shapes -> {
                shapes.put(Direction.NORTH, NORTH_SHAPE);
                shapes.put(Direction.EAST, EAST_SHAPE);
                shapes.put(Direction.SOUTH, SOUTH_SHAPE);
                shapes.put(Direction.WEST, WEST_SHAPE);
                shapes.put(Direction.UP, DOWN_SHAPE);
                shapes.put(Direction.DOWN, UP_SHAPE);
            });

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public EntityInteractionPanelBlock(Properties settings) {
        super(settings.noOcclusion().noCollission());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return SHAPES_FOR_DIRECTIONS.getOrDefault(facing, Shapes.block());
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
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(facing.getOpposite());
        return Block.canSupportCenter(world, attachedPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
            BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite() && !state.canSurvive(world, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
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
}
