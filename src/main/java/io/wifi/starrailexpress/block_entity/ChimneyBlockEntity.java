package io.wifi.starrailexpress.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ChimneyBlockEntity extends SyncingBlockEntity {

    public ChimneyBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.CHIMNEY, pos, state);
    }

    public static <T extends BlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T t) {
        world.addParticle(TMMParticles.BLACK_SMOKE, pos.getX() + .5f, pos.getY(), pos.getZ() + .5f, 0, 0, 0);
    }
}
