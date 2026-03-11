package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.index.TMMProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class TrimmedStairsBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty SUPPORT = TMMProperties.SUPPORT;
    public static final BooleanProperty LEFT = TMMProperties.LEFT;
    public static final BooleanProperty RIGHT = TMMProperties.RIGHT;

    public static final VoxelShape BOTTOM = Block.box(0, 0, 0, 16, 6, 16);
    public static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0, 6, -2, 16, 8, 8),
            Block.box(0, 14, 6, 16, 16, 16)
    );
    public static final VoxelShape NORTH_SUPPORTED_SHAPE = Shapes.or(
            NORTH_SHAPE,
            BOTTOM,
            Block.box(0, 6, 8, 16, 14, 16)
    );
    public static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(8, 6, 0, 18, 8, 16),
            Block.box(0, 14, 0, 10, 16, 16)
    );
    public static final VoxelShape EAST_SUPPORTED_SHAPE = Shapes.or(
            EAST_SHAPE,
            BOTTOM,
            Block.box(0, 6, 0, 8, 14, 16)
    );
    public static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(0, 6, 8, 16, 8, 18),
            Block.box(0, 14, 0, 16, 16, 10)
    );
    public static final VoxelShape SOUTH_SUPPORTED_SHAPE = Shapes.or(
            SOUTH_SHAPE,
            BOTTOM,
            Block.box(0, 6, 0, 16, 14, 8)
    );
    public static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(-2, 6, 0, 8, 8, 16),
            Block.box(6, 14, 0, 16, 16, 16)
    );
    public static final VoxelShape WEST_SUPPORTED_SHAPE = Shapes.or(
            WEST_SHAPE,
            BOTTOM,
            Block.box(8, 6, 0, 16, 14, 16)
    );

    public TrimmedStairsBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SUPPORT, false)
                .setValue(LEFT, true)
                .setValue(RIGHT, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return null;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing.getClockWise() && state.getValue(LEFT)) {
            return state.setValue(LEFT, !neighborState.is(this) || neighborState.getValue(FACING) != facing);
        } else if (direction == facing.getCounterClockWise() && state.getValue(RIGHT)) {
            return state.setValue(RIGHT, !neighborState.is(this) || neighborState.getValue(FACING) != facing);
        }
        return state;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        BlockState leftState = world.getBlockState(pos.relative(facing.getClockWise()));
        BlockState rightState = world.getBlockState(pos.relative(facing.getCounterClockWise()));
        return this.defaultBlockState()
                .setValue(SUPPORT, ctx.isSecondaryUseActive())
                .setValue(FACING, facing)
                .setValue(LEFT, !leftState.is(this) || leftState.getValue(FACING) != facing)
                .setValue(RIGHT, !rightState.is(this) || rightState.getValue(FACING) != facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean support = state.getValue(SUPPORT);
        return switch (state.getValue(FACING)) {
            case NORTH -> support ? NORTH_SUPPORTED_SHAPE : NORTH_SHAPE;
            case EAST -> support ? EAST_SUPPORTED_SHAPE : EAST_SHAPE;
            case SOUTH -> support ? SOUTH_SUPPORTED_SHAPE : SOUTH_SHAPE;
            default -> support ? WEST_SUPPORTED_SHAPE : WEST_SHAPE;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SUPPORT, LEFT, RIGHT);
    }
}
