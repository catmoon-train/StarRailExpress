package io.wifi.starrailexpress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.state.BlockState;

public class LightBarrierBlock extends BarrierBlock {
    public LightBarrierBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 15;
    }
}