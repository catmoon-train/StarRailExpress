package io.wifi.starrailexpress.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public interface CrosshairEnabling {

    boolean shouldShowCrosshair(Level world, BlockState state, BlockHitResult hit);

}
