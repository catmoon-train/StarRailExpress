package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModSceneBlocks;

public class WaterPumpBlockEntity extends BlockEntity {
    private static final int COOLDOWN_TICKS = 30 * 20;
    private int clicks;
    private int cooldownTicks;

    public WaterPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.WATER_PUMP_ENTITY, pos, state);
    }

    public int click() {
        if (cooldownTicks > 0) return -1;
        clicks++;
        setChanged();
        return clicks;
    }

    public void startCooldown() {
        clicks = 0;
        cooldownTicks = COOLDOWN_TICKS;
        setChanged();
    }

    public boolean isCoolingDown() {
        return cooldownTicks > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterPumpBlockEntity be) {
        if (be.cooldownTicks > 0) {
            be.cooldownTicks--;
            if (be.cooldownTicks == 0) be.setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Clicks", clicks);
        tag.putInt("CooldownTicks", cooldownTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clicks = tag.getInt("Clicks");
        cooldownTicks = tag.getInt("CooldownTicks");
    }
}
