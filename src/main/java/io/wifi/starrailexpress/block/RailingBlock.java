package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class RailingBlock extends AbstractRailingBlock {

    protected static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 0, 16, 16, 2);
    protected static final VoxelShape EAST_SHAPE = Block.box(14, 0, 0, 16, 16, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 14, 16, 16, 16);
    protected static final VoxelShape WEST_SHAPE = Block.box(0, 0, 0, 2, 16, 16);
    private final Block diagonalRailingBlock;
    private final Block postBlock;

    public RailingBlock(Block diagonalRailingBlock, Block postBlock, Properties settings) {
        super(settings);
        this.diagonalRailingBlock = diagonalRailingBlock;
        this.postBlock = postBlock;
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
        return this.getShape(state, world, pos, context);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) return null;
        if (ctx.isSecondaryUseActive()) return this.postBlock.getStateForPlacement(ctx);
        BlockState diagonalState = this.diagonalRailingBlock.getStateForPlacement(ctx);
        return diagonalState == null ? state : diagonalState;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return null;
    }
}
