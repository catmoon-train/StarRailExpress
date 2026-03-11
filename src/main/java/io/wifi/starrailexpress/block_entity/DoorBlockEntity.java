package io.wifi.starrailexpress.block_entity;

import io.wifi.starrailexpress.block.DoorPartBlock;
import io.wifi.starrailexpress.block.SmallDoorBlock;
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

    private String keyName = "";

    private int closeCountdown = 0;
    private int jammedTime = 0;
    private boolean blasted = false;

    public DoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.open = state.getValue(BlockStateProperties.OPEN);
        this.state.start(this.age);
        this.state.fastForward(10, 1);
    }

    public static <T extends DoorBlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T entity) {
        entity.age++;
    }

    public static <T extends DoorBlockEntity> void serverTick(Level world, BlockPos pos, BlockState state, T entity) {
        if (state.getValue(DoorPartBlock.OPEN) && !entity.isBlasted()) {
            entity.setCloseCountdown(entity.getCloseCountdown() - 1);
            if (entity.getCloseCountdown() <= 0) {
                SmallDoorBlock.toggleDoor(state, world, (SmallDoorBlockEntity) entity, pos);
            }
        } else {
            entity.setCloseCountdown(0);
        }

        if (entity.isJammed()) {
            entity.setJammed(entity.getJammedTime() - 1);
        }
    }

    public void toggle(boolean silent) {
        if (this.level == null || this.level.getGameTime() == this.lastUpdate || this.isBlasted()) {
            return;
        }
        this.toggleOpen();
        if (!silent) {
            this.playToggleSound();
        }
        this.toggleBlocks();
    }

    protected void toggleOpen() {
        if (this.level != null) {
            this.lastUpdate = this.level.getGameTime();
            this.open = !this.open;
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, this.open ? 1 : 0);
            this.closeCountdown = this.open ? GameConstants.DOOR_AUTOCLOSE_TIME : 0;
        }
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
        nbt.putString("keyName", this.getKeyName());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.setOpen(nbt.getBoolean("open"));
        this.setBlasted(nbt.getBoolean("blasted"));
        this.setCloseCountdown(nbt.getInt("closeCountdown"));
        this.setJammed(nbt.getInt("jammedTime"));
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
        if (!this.open) {
            this.toggle(false);
        }
        this.setBlasted(true);
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
}
