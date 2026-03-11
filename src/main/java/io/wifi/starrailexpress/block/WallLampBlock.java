package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallLampBlock extends ToggleableFacingLightBlock {

    public static final VoxelShape FLOOR_SHAPE = Shapes.or(
            Block.box(6, 0, 6, 10, 10, 10),
            Block.box(5, 1, 5, 11, 9, 11)
    );
    public static final VoxelShape CEILING_SHAPE = Shapes.or(
            Block.box(6, 6, 6, 10, 16, 10),
            Block.box(5, 7, 5, 11, 15, 11)
    );
    public static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(6, 3, 11, 10, 13, 15),
            Block.box(5, 4, 10, 11, 12, 16)
    );
    public static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(1, 3, 6, 5, 13, 10),
            Block.box(0, 4, 5, 6, 12, 11)
    );
    public static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(6, 3, 1, 10, 13, 5),
            Block.box(5, 4, 0, 11, 12, 6)
    );
    public static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(11, 3, 6, 15, 13, 10),
            Block.box(10, 4, 5, 16, 12, 11)
    );

    public WallLampBlock(Properties settings) {
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
}
