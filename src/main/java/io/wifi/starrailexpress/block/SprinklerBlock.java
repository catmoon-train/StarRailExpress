package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.block_entity.SprinklerBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SprinklerBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<SprinklerBlock> CODEC = simpleCodec(SprinklerBlock::new);
    protected static final VoxelShape UP_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0);
    protected static final VoxelShape DOWN_SHAPE = Block.box(3.0, 13.0, 3.0, 13.0, 16.0, 13.0);
    protected static final VoxelShape EAST_SHAPE = Block.box(0.0, 3.0, 3.0, 3.0, 13.0, 13.0);
    protected static final VoxelShape WEST_SHAPE = Block.box(13.0, 3.0, 3.0, 16.0, 13.0, 13.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(3.0, 3.0, 0.0, 13.0, 13.0, 3.0);
    protected static final VoxelShape NORTH_SHAPE = Block.box(3.0, 3.0, 13.0, 13.0, 13.0, 16.0);

    public SprinklerBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.getStateDefinition().any().setValue(POWERED, false).setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL));
    }

    public static Direction getConnectedDirection(BlockState state) {
        return switch (state.getValue(FACE)) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            default -> state.getValue(FACING);
        };
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (getConnectedDirection(state)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state != null) {
            return state.setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
        }
        return null;
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        if (!world.isClientSide) {
            if (world.hasNeighborSignal(pos) || world.hasNeighborSignal(fromPos)) {
                world.scheduleTick(pos, this, 4);
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BlockState cycle = state.cycle(POWERED);
        world.setBlock(pos, cycle, Block.UPDATE_CLIENTS);
        world.getBlockEntity(pos, TMMBlockEntities.SPRINKLER).ifPresent(entity -> {
            entity.setPowered(cycle.getValue(POWERED));
            entity.sync();
        });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, FACING, FACE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SprinklerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClientSide || !type.equals(TMMBlockEntities.SPRINKLER)) {
            return null;
        }
        return SprinklerBlockEntity::clientTick;
    }
}
