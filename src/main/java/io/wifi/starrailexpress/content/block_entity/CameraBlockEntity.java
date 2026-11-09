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

    /** 中控员「动态模块」安装标记：允许监控画面上下左右 / 斜向转动视角。 */
    private boolean dynamicModule = false;
    /** 中控员「红外线扫描仪」安装标记：监控画面自带夜视，进入监控区域的玩家发光。 */
    private boolean infraredScanner = false;
    /** 中控员「激光模块」安装标记：监控画面可用技能键发射激光。 */
    private boolean laserModule = false;
    /** 激光模块剩余冷却（tick），大于 0 时无法再次发射。 */
    private int laserCooldown = 0;
    /** 激光模块发射后的冷却时长（tick）：120 秒。 */
    public static final int LASER_MODULE_COOLDOWN_TICKS = 120 * 20;

    // ==================== 中控员模块 ====================

    /** 是否装有动态模块。 */
    public boolean hasDynamicModule() {
        return dynamicModule;
    }

    /** 是否装有红外线扫描仪。 */
    public boolean hasInfraredScanner() {
        return infraredScanner;
    }

    /** 是否装有激光模块。 */
    public boolean hasLaserModule() {
        return laserModule;
    }

    /** 安装动态模块（视角偏移是纯客户端状态，这里只标记模块已装）。 */
    public void installDynamicModule() {
        this.dynamicModule = true;
        syncToClient();
    }

    /** 安装红外线扫描仪。 */
    public void installInfraredScanner() {
        this.infraredScanner = true;
        syncToClient();
    }

    /** 安装激光模块（新装的模块冷却清零，可立刻使用一次）。 */
    public void installLaserModule() {
        this.laserModule = true;
        this.laserCooldown = 0;
        syncToClient();
    }

    /** 当前是否真的能转动视角：装了动态模块且摄像头没失灵。 */
    public boolean canRotateView() {
        return this.dynamicModule && !isBroken();
    }

    /** 激光模块剩余冷却（tick）。 */
    public int getLaserCooldown() {
        return this.laserCooldown;
    }

    /** 记录一次激光发射并开始 120 秒冷却。 */
    public void markLaserFired() {
        this.laserCooldown = LASER_MODULE_COOLDOWN_TICKS;
        syncToClient();
    }


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
    /** 移除中控员装在本摄像头上的全部模块，并复位激光冷却。 */
    public void clearModules() {
        this.dynamicModule = false;
        this.infraredScanner = false;
        this.laserModule = false;
        this.laserCooldown = 0;
    }

    /**
     * 每局开始 / 结束时由 {@code CameraBlock#onResetBlockEntity} 调用：
     * 模块状态属于「本局内的方块状态」，必须随游戏开始与结束一起初始化重置。
     */
    public void reset() {
        this.broken = 0;
        clearModules();
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
        // 激光模块冷却只在服务端权威推进；客户端靠方块实体同步拿到剩余值。
        if (!world.isClientSide && cameraBlockEntity.laserCooldown > 0) {
            cameraBlockEntity.laserCooldown--;
            if (cameraBlockEntity.laserCooldown == 0) {
                cameraBlockEntity.syncToClient();
            } else if (cameraBlockEntity.laserCooldown % 20 == 0) {
                // 每秒同步一次倒计时，HUD 才能显示剩余秒数；不必每 tick 发包。
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
        tag.putBoolean("dynamic_module", dynamicModule);
        tag.putBoolean("infrared_scanner", infraredScanner);
        tag.putBoolean("laser_module", laserModule);
        tag.putInt("laser_cooldown", laserCooldown);
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
        this.dynamicModule = tag.getBoolean("dynamic_module");
        this.infraredScanner = tag.getBoolean("infrared_scanner");
        this.laserModule = tag.getBoolean("laser_module");
        this.laserCooldown = tag.getInt("laser_cooldown");
    }

    // 简洁的同步方法
    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            this.setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}