package io.wifi.starrailexpress.content.block_entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LockableButtonBlockEntity extends SmallDoorBlockEntity {

    public LockableButtonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void toggleBlocks() {
    }

    @Override
    protected void toggleOpen(int ticks) {
    }


    public static <T extends LockableButtonBlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T entity) {
        entity.age++;
    }

    public static <T extends LockableButtonBlockEntity> void serverTick(Level world, BlockPos pos, BlockState state, T entity) {
        if (entity.isJammed()) {
            entity.setJammed(entity.getJammedTime() - 1);
        }
    }
}
