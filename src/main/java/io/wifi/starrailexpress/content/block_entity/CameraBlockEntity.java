package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
        this.setChanged();
        syncToClient();
    }

    public void reset() {
        this.broken = 0;
    }

    public static void tick(Level world, BlockPos pos, BlockState state, CameraBlockEntity cameraBlockEntity) {
        if (cameraBlockEntity.broken > 0) {
            cameraBlockEntity.broken--;
            if (cameraBlockEntity.broken == 0) {
                cameraBlockEntity.setChanged();
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
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}