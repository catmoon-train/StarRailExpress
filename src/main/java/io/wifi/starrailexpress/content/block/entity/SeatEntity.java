/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.block.entity;

import io.wifi.starrailexpress.content.block.MountableBlock;
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
