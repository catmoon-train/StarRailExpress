package io.wifi.starrailexpress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BarStoolBlock extends MountableBlock {
    private static final Vec3 SIT_POS = new Vec3(0.5f, -0.2f, 0.5f);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(6, 0, 6, 10, 1, 10),
            Block.box(7, 1, 7, 9, 9, 9),
            Block.box(4, 4, 4, 12, 5, 12),
            Block.box(3, 9, 3, 13, 12, 13)
    );

    public BarStoolBlock(Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Vec3 getSitPos(Level world, BlockState state, BlockPos pos) {
        return SIT_POS;
    }
}
