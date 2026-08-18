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

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

public class SREGameTimeComponent implements AutoSyncedComponent, CommonTickingComponent {
    public static final ComponentKey<SREGameTimeComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("time"),
            SREGameTimeComponent.class);
    public final Level world;
    public int resetTime = 0;
    public int time = 0;
    /** 游戏开始（计时器启动）时的世界 gameTime，用于「开局冷却」基准，不受击杀加时影响。 */
    public long startWorldTick = 0;
    public boolean timeFrozen = false;
    public boolean levelGameTimeFrozen = false;
    protected long tickCount = 0;

    public SREGameTimeComponent(Level world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void reset() {
        this.startWorldTick = this.world.getGameTime();
        this.timeFrozen = false;
        this.setServerFrozen(false);
        this.setTime(this.resetTime);
        this.levelGameTimeFrozen = false;
        this.tickCount = 0;
    }

    public int getResetTime() {
        return this.resetTime;
    }

    public long getStartWorldTick() {
        return this.startWorldTick;
    }

    public void setLevelGameTimeFrozen(boolean frozen) {
        setLevelGameTimeFrozen(frozen, true);
    }

    public void setLevelGameTimeFrozen(boolean frozen, boolean sync) {
        levelGameTimeFrozen = frozen;
        if (sync)
            sync();
    }

    public void setServerFrozen(boolean frozen) {
        world.tickRateManager().setFrozen(frozen);
    }

    public void setTimeFrozen(boolean frozen) {
        setTimeFrozen(frozen, true);
    }

    public void setTimeFrozen(boolean frozen, boolean sync) {
        this.timeFrozen = frozen;
        if (sync)
            sync();
    }

    public boolean isTimeFrozen() {
        return this.timeFrozen || levelGameTimeFrozen || world.tickRateManager().isFrozen();
    }

    @Override
    public void tick() {
        if (isTimeFrozen()) {
            return;
        }
        tickCount++;
        if (!SREGameWorldComponent.KEY.get(this.world).isRunning())
            return;
        if (this.time <= 0)
            return;
        this.time--;
        // 从每400tick增加到每600tick同步（30秒）
        if (this.time % 600 == 0)
            this.sync();
    }

    public boolean hasTime() {
        return this.time > 0;
    }

    public int getTime() {
        return this.time;
    }

    public void addTime(int time) {
        this.setTime(this.time + time);
    }

    public void setResetTime(int time) {
        this.resetTime = time;
    }

    public void setTime(int time) {
        this.time = time;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("frozen", this.timeFrozen);
        tag.putBoolean("lt_frozen", this.levelGameTimeFrozen);
        tag.putInt("resetTime", this.resetTime);
        tag.putInt("time", this.time);
        tag.putLong("startWorldTick", this.startWorldTick);
        tag.putLong("tickCount", this.tickCount);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.tickCount = tag.contains("tickCount") ? tag.getLong("tickCount") : 0;
        this.timeFrozen = tag.contains("frozen") && tag.getBoolean("frozen");
        this.levelGameTimeFrozen = tag.contains("lt_frozen") && tag.getBoolean("lt_frozen");

        this.resetTime = tag.contains("resetTime") ? tag.getInt("resetTime") : 0;
        this.time = tag.contains("time") ? tag.getInt("time") : 0;
        this.startWorldTick = tag.contains("startWorldTick") ? tag.getLong("startWorldTick") : 0L;
    }

    public long getTicksFromGameStart() {
        return this.tickCount;
    }
}