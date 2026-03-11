package io.wifi.starrailexpress.block.entity;

import io.wifi.starrailexpress.block.MountableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class SeatEntity extends Entity {
    @Nullable
    BlockPos seatPos;

    public SeatEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        NbtUtils.readBlockPos(nbt, "seatPos").ifPresent(this::setSeatPos);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        if (this.getSeatPos() != null) nbt.put("seatPos", NbtUtils.writeBlockPos(this.getSeatPos()));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            if (this.getSeatPos() == null || !this.isVehicle() || !(this.level().getBlockState(this.getSeatPos()).getBlock() instanceof MountableBlock)) {
                this.ejectPassengers();
                this.discard();
            }
        }

        super.tick();
    }


    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return false;
    }

    @Nullable
    public BlockPos getSeatPos() {
        return seatPos;
    }

    public void setSeatPos(@Nullable BlockPos seatPos) {
        this.seatPos = seatPos;
    }
}
