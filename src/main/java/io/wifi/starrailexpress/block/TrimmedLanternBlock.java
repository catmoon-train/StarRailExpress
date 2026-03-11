package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class TrimmedLanternBlock extends ToggleableFacingLightBlock {
    protected static final VoxelShape FLOOR_SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 10, 13),
            Block.box(2, 4, 2, 14, 6, 14)
    );
    protected static final VoxelShape CEILING_SHAPE = Shapes.or(
            Block.box(3, 6, 3, 13, 16, 13),
            Block.box(2, 10, 2, 14, 12, 14)
    );
    protected static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(3, 3, 10, 13, 13, 14),
            Block.box(2, 2, 14, 14, 14, 16)
    );
    protected static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(2, 3, 3, 6, 13, 13),
            Block.box(0, 2, 2, 2, 14, 14)
    );
    protected static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(3, 3, 2, 13, 13, 6),
            Block.box(2, 2, 0, 14, 14, 2)
    );
    protected static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(10, 3, 3, 14, 13, 13),
            Block.box(14, 2, 2, 16, 14, 14)
    );

    public TrimmedLanternBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return null;
    }


    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case UP -> FLOOR_SHAPE;
            case DOWN -> CEILING_SHAPE;
        };
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        super.triggerEvent(state, world, pos, type, data);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(type, data);
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof MenuProvider ? (MenuProvider) blockEntity : null;
    }
}
