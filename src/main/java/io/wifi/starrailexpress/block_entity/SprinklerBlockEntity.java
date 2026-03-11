package io.wifi.starrailexpress.block_entity;

import io.wifi.starrailexpress.block.SprinklerBlock;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SprinklerBlockEntity extends SyncingBlockEntity {

    private boolean powered;

    public SprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.SPRINKLER, pos, state);
        this.setPowered(state.getValue(SprinklerBlock.POWERED));
    }

    public static <T extends BlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T t) {
        SprinklerBlockEntity entity = (SprinklerBlockEntity) t;
        if (!entity.isPowered()) {
            return;
        }
        Direction direction = SprinklerBlock.getConnectedDirection(state);
        RandomSource random = world.getRandom();

        float offsetScale = .2f;
        float randomOffsetScale = .2f;
        float velScale = 0.5f;
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        for (int i = 0; i < 5; i++) {
            world.addParticle(direction == Direction.DOWN ? ParticleTypes.FALLING_WATER : ParticleTypes.SPLASH, true,
                    x - direction.getStepX() * offsetScale
                            + ((random.nextFloat() * 2f - 1f)
                                    * (direction.getAxis() != Direction.Axis.X ? randomOffsetScale : 0)),
                    (direction == Direction.DOWN ? .5 : .6) + y - direction.getStepY() * offsetScale
                            + ((random.nextFloat() * 2f - 1f)
                                    * (direction.getAxis() != Direction.Axis.Y ? randomOffsetScale : 0)),
                    z - direction.getStepZ() * offsetScale
                            + ((random.nextFloat() * 2f - 1f)
                                    * (direction.getAxis() != Direction.Axis.Z ? randomOffsetScale : 0)),
                    direction.getStepX() * velScale,
                    direction.getStepY() * velScale * (direction == Direction.UP ? 20f : 0),
                    direction.getStepZ() * velScale);
        }
    }

    public boolean isPowered() {
        return this.powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        nbt.putBoolean("powered", this.isPowered());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        this.setPowered(nbt.getBoolean("powered"));
    }
}
