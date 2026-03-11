package io.wifi.starrailexpress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class LoungeCouch extends CouchBlock {
    public LoungeCouch(Properties settings) {
        super(settings);
    }

    @Override
    public Vec3 getNorthFacingSitPos(Level world, BlockState state, BlockPos pos) {
        return new Vec3(0.5f, -0.5f, 0.6f);
    }
}
