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

package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CameraBlockEntity extends BlockEntity {
    private Direction facing = Direction.NORTH;
    private int broken = 0;

    public boolean isBroken() {
        return broken > 0;
    }

    public int getBrokenTime() {
        return broken;
    }

    public void setBroken(int time) {
        this.broken = time;
        syncToClient();
    }

    public void reset() {
        this.broken = 0;
        syncToClient();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveWithoutMetadata(registryLookup);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, CameraBlockEntity cameraBlockEntity) {
        if (cameraBlockEntity.broken > 0) {
            cameraBlockEntity.broken--;
            if (cameraBlockEntity.broken == 0) {
                cameraBlockEntity.syncToClient();
            }
        }
    }

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.CAMERA, pos, state);
        // 从BlockState中获取方向
        if (state.hasProperty(io.wifi.starrailexpress.content.block.CameraBlock.FACING)) {
            this.facing = state.getValue(io.wifi.starrailexpress.content.block.CameraBlock.FACING);
        }
    }

    public Direction getFacing() {
        return facing;
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("facing", facing.getName());
        tag.putInt("broken", broken);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("broken")) {
            this.broken = tag.getInt("broken");
        }
        if (tag.contains("facing")) {
            this.facing = Direction.valueOf(tag.getString("facing").toUpperCase());
        } else {
            // 从BlockState获取默认方向（兼容旧版本）
            if (this.getBlockState().hasProperty(io.wifi.starrailexpress.content.block.CameraBlock.FACING)) {
                this.facing = this.getBlockState().getValue(io.wifi.starrailexpress.content.block.CameraBlock.FACING);
            }
        }
    }

    // 简洁的同步方法
    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            this.setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}