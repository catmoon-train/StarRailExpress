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

import io.wifi.starrailexpress.content.block.DoorPartBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public abstract class DoorBlockEntity extends SyncingBlockEntity {

    public AnimationState state = new AnimationState();
    protected long lastUpdate = 0L;
    protected boolean open;
    protected int age = 0;

    @Override
    public boolean isValidBlockState(BlockState blockState) {
        return true;
    }

    protected String keyName = "";

    protected int closeCountdown = 0;
    protected int jammedTime = 0;
    protected boolean blasted = false;
    protected int cooldown = 0;

    // 通用物证·破门痕（第4批）：被工具强行打开的记录
    protected long tamperedTime = -1L; // 被破坏的游戏刻；-1=未被破坏
    protected byte tamperedTool = 0; // 0=无 1=撬棍 2=开锁器

    public DoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.open = state.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
        this.state.start(this.age);
        this.state.fastForward(10, 1);
    }

    public static <T extends DoorBlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T entity) {
        entity.age++;
    }

    public static <T extends DoorBlockEntity> void serverTick(Level world, BlockPos pos, BlockState state, T entity) {
        if (entity.cooldown > 0) {
            entity.cooldown--;
        }
        if (state.getValue(DoorPartBlock.OPEN) && !entity.isBlasted()) {
            if (entity.getCloseCountdown() >= 0) {
                entity.setCloseCountdown(entity.getCloseCountdown() - 1);
                if (entity.getCloseCountdown() <= 0) {
                    if (state.getBlock() instanceof SmallDoorBlock sb)
                        sb.toggleDoor(state, world, (SmallDoorBlockEntity) entity, pos);
                }
            }
        } else {
            entity.setCloseCountdown(0);
        }

        if (entity.isJammed()) {
            entity.setJammed(entity.getJammedTime() - 1);
        }
    }

    public void toggle(boolean silent, int ticks) {
        if (this.level == null || this.level.getGameTime() == this.lastUpdate || this.isBlasted()) {
            return;
        }
        if (ticks != -2) {
            this.toggleOpen(ticks);
        } else {
            this.toggleOpen();
        }
        if (!silent) {
            this.playToggleSound();
        }
        this.toggleBlocks();
    }

    public void toggle(boolean silent) {
        toggle(silent, -2);
    }

    protected void toggleOpen(int ticks) {
        if (this.level != null) {
            this.lastUpdate = this.level.getGameTime();
            this.open = !this.open;
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, this.open ? 1 : 0);
            this.closeCountdown = this.open ? ticks : 0;
        }
    }

    protected void toggleOpen() {
        toggleOpen(GameConstants.DOOR_AUTOCLOSE_TIME);
    }

    protected void playToggleSound() {
        if (this.level == null) {
            return;
        }
        this.level.playSound(null, this.worldPosition, TMMSounds.BLOCK_DOOR_TOGGLE, SoundSource.BLOCKS, 1f, 1f);
    }

    protected abstract void toggleBlocks();

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public float getYaw() {
        return 180 - this.getFacing().toYRot();
    }

    public boolean getOpen() {
        return this.getBlockState().getValue(BlockStateProperties.OPEN);
    }

    public Direction getFacing() {
        return this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    public int getAge() {
        return this.age;
    }

    @Override
    public boolean triggerEvent(int type, int data) {
        if (this.level != null && type == 1) {
            this.state.start(this.age);
            this.open = data != 0;
            return true;
        }
        return super.triggerEvent(type, data);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putBoolean("open", this.isOpen());
        nbt.putBoolean("blasted", this.isBlasted());
        nbt.putInt("closeCountdown", this.getCloseCountdown());
        nbt.putInt("jammedTime", this.getJammedTime());
        nbt.putLong("tamperedTime", this.tamperedTime);
        nbt.putByte("tamperedTool", this.tamperedTool);
        nbt.putString("keyName", this.getKeyName());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.setOpen(nbt.getBoolean("open"));
        this.setBlasted(nbt.getBoolean("blasted"));
        this.setCloseCountdown(nbt.getInt("closeCountdown"));
        this.setJammed(nbt.getInt("jammedTime"));
        this.tamperedTime = nbt.contains("tamperedTime") ? nbt.getLong("tamperedTime") : -1L;
        this.tamperedTool = nbt.getByte("tamperedTool");
        this.setKeyName(nbt.getString("keyName"));
    }

    public String getKeyName() {
        return this.keyName;
    }

    public void setKeyName(String string) {
        this.keyName = string;
    }

    public int getCloseCountdown() {
        return closeCountdown;
    }

    public void setCloseCountdown(int closeCountdown) {
        this.closeCountdown = closeCountdown;
    }

    public void setJammed(int time) {
        this.jammedTime = time;
    }

    public void jam() {
        this.setJammed(GameConstants.JAMMED_DOOR_TIME);
        if (this.open) {
            this.toggle(false);
        }
    }

    public void blast() {
        this.setJammed(0);
        if (!this.open) {
            this.toggle(false);
        }
        this.setBlasted(true);
        recordTamper((byte) 1);
    }

    public boolean isJammed() {
        return this.jammedTime > 0;
    }

    public int getJammedTime() {
        return jammedTime;
    }

    public boolean isBlasted() {
        return blasted;
    }

    public void setBlasted(boolean blasted) {
        this.blasted = blasted;
    }

    // ===== 通用物证·破门痕 =====
    public long getTamperedTime() {
        return this.tamperedTime;
    }

    public byte getTamperedTool() {
        return this.tamperedTool;
    }

    /** 记录本门被工具强行打开（1=撬棍 2=开锁器）。仅服务端记录并同步；受物证开关控制。 */
    public void recordTamper(byte tool) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        io.wifi.starrailexpress.SREConfig cfg = io.wifi.starrailexpress.SREConfig.instance();
        if (cfg == null || !cfg.enableForensicEvidence || !cfg.forensicDoorMark) {
            return;
        }
        this.tamperedTime = this.level.getGameTime();
        this.tamperedTool = tool;
        this.sync();
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public int getCooldown() {
        return this.cooldown;
    }

    public boolean isInCooldown() {
        return this.cooldown > 0;
    }
}
