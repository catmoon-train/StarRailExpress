package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BeveragePlateBlockEntity extends PlateTrayBlockEntity {
    public BeveragePlateBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.BEVERAGE_PLATE, pos, state);
    }

    public BeveragePlateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
