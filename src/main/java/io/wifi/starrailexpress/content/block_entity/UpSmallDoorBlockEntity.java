package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.content.block.DoorPartBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class UpSmallDoorBlockEntity extends SmallDoorBlockEntity {

    public UpSmallDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void toggleBlocks() {
        if (this.level == null) {
            return;
        }
        this.level.setBlock(this.worldPosition, this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        this.level.setBlock(this.worldPosition.above(), this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open)
                .setValue(SmallDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    protected void toggleOpen(int ticks) {
        super.toggleOpen(ticks);
        if (this.level == null) {
            return;
        }
        Direction facing = this.getFacing();
        BlockPos neighborPos = this.getBlockPos().relative(facing.getCounterClockWise());
        BlockState neighborState = this.level.getBlockState(neighborPos);
        if (neighborState.is(this.getBlockState().getBlock())
                && neighborState.getValue(DoorPartBlock.FACING).getOpposite() == facing
                && this.level.getBlockEntity(neighborPos) instanceof UpSmallDoorBlockEntity neighborEntity) {
            neighborEntity.toggle(true, ticks);
        }
    }
}
