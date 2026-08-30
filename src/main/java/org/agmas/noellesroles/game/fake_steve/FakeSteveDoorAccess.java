package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Identifies blocks that the possessed body may open while following a route. */
final class FakeSteveDoorAccess {
    private FakeSteveDoorAccess() {
    }

    static boolean isOpenablePassage(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof SmallDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock
                // Covers registered glass/sliding doors and future door implementations
                // that expose the standard open property without subclassing vanilla DoorBlock.
                || state.hasProperty(BlockStateProperties.OPEN);
    }

    static boolean isOpen(BlockState state) {
        return !state.hasProperty(BlockStateProperties.OPEN)
                || state.getValue(BlockStateProperties.OPEN);
    }
}
