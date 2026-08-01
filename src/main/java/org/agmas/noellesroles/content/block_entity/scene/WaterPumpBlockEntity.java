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
