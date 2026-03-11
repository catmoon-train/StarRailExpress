package io.wifi.starrailexpress.block_entity;

import io.wifi.starrailexpress.block.DoorPartBlock;
import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class SmallDoorBlockEntity extends DoorBlockEntity {

    public SmallDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static SmallDoorBlockEntity createGlass(BlockPos pos, BlockState state) {
        return new SmallDoorBlockEntity(TMMBlockEntities.SMALL_GLASS_DOOR, pos, state);
    }

    public static SmallDoorBlockEntity createWood(BlockPos pos, BlockState state) {
        return new SmallDoorBlockEntity(TMMBlockEntities.SMALL_WOOD_DOOR, pos, state);
    }

    @Override
    protected void toggleBlocks() {
        if (this.level == null) {
            return;
        }
        this.level.setBlock(this.worldPosition, this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        this.level.setBlock(this.worldPosition.above(), this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open).setValue(SmallDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    protected void toggleOpen() {
        super.toggleOpen();
        if (this.level == null) {
            return;
        }
        Direction facing = this.getFacing();
        BlockPos neighborPos = this.getBlockPos().relative(facing.getCounterClockWise());
        BlockState neighborState = this.level.getBlockState(neighborPos);
        if (neighborState.is(this.getBlockState().getBlock())
                && neighborState.getValue(DoorPartBlock.FACING).getOpposite() == facing
                && this.level.getBlockEntity(neighborPos) instanceof SmallDoorBlockEntity neighborEntity) {
            neighborEntity.toggle(true);
        }
    }
}
