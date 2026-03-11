package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LedgeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LedgeBlock> CODEC = simpleCodec(LedgeBlock::new);

    protected static final VoxelShape NORTH_SHAPE = Block.box(0, 14, 0, 16, 16, 8);
    protected static final VoxelShape EAST_SHAPE = Block.box(8, 14, 0, 16, 16, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(0, 14, 8, 16, 16, 16);
    protected static final VoxelShape WEST_SHAPE = Block.box(0, 14, 0, 8, 16, 16);

    protected static final VoxelShape NORTH_SHAPE_SMALL = Block.box(0, 14, 0, 16, 16, 2);
    protected static final VoxelShape EAST_SHAPE_SMALL = Block.box(14, 14, 0, 16, 16, 16);
    protected static final VoxelShape SOUTH_SHAPE_SMALL = Block.box(0, 14, 14, 16, 16, 16);
    protected static final VoxelShape WEST_SHAPE_SMALL = Block.box(0, 14, 0, 2, 16, 16);

    public LedgeBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE_SMALL;
            case SOUTH -> SOUTH_SHAPE_SMALL;
            case WEST -> WEST_SHAPE_SMALL;
            case EAST -> EAST_SHAPE_SMALL;
            default -> null;
        };
    }

    public VoxelShape getCollisionShapeBig(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> null;
        };
    }

    public VoxelShape getCollisionShapeSmall(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE_SMALL;
            case SOUTH -> SOUTH_SHAPE_SMALL;
            case WEST -> WEST_SHAPE_SMALL;
            case EAST -> EAST_SHAPE_SMALL;
            default -> null;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!context.isAbove(state.getShape(world, pos), pos, true)) {
            return getCollisionShapeSmall(state, world, pos, context);
        }
        return getCollisionShapeBig(state, world, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
