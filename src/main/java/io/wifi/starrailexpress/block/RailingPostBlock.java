package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class RailingPostBlock extends AbstractRailingBlock {

    protected static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 0, 2, 16, 2);
    protected static final VoxelShape EAST_SHAPE = Block.box(14, 0, 0, 16, 16, 2);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(14, 0, 14, 16, 16, 16);
    protected static final VoxelShape WEST_SHAPE = Block.box(0, 0, 14, 2, 16, 16);
    protected static final VoxelShape NORTH_COLLISION_SHAPE = Block.box(0, 0, 0, 2, 24, 2);
    protected static final VoxelShape EAST_COLLISION_SHAPE = Block.box(14, 0, 0, 16, 24, 2);
    protected static final VoxelShape SOUTH_COLLISION_SHAPE = Block.box(14, 0, 14, 16, 24, 16);
    protected static final VoxelShape WEST_COLLISION_SHAPE = Block.box(0, 0, 14, 2, 24, 16);

    public RailingPostBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            default -> WEST_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_COLLISION_SHAPE;
            case EAST -> EAST_COLLISION_SHAPE;
            case SOUTH -> SOUTH_COLLISION_SHAPE;
            default -> WEST_COLLISION_SHAPE;
        };
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        return state.setValue(FACING, Direction.fromYRot(ctx.getRotation() + 45d));
    }
}
