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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class SRETrainWorldComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SRETrainWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("train"),
            SRETrainWorldComponent.class);

    private final Level world;
    private int speed = 0; // im km/h
    private int time = 0;
    private boolean snow = false;
    private boolean sand = false;
    private boolean fog = true;
    private boolean hud = true;
    private TimeOfDay timeOfDay = TimeOfDay.DAY;

    public SRETrainWorldComponent(Level world) {
        this.world = world;
    }

    private boolean needsSync = false;

    private void sync() {
        SRETrainWorldComponent.KEY.sync(this.world);
        this.needsSync = false;
    }

    private void markDirty() {
        this.needsSync = true;
    }

    public void setSpeed(int speed) {
        if (this.speed != speed) {
            this.speed = speed;
            this.markDirty();
        }
    }

    public int getSpeed() {
        return speed;
    }

    public float getTime() {
        return time;
    }

    public void setTime(int time) {
        if (this.time != time) {
            this.time = time;
            this.markDirty();
        }
    }

    public boolean isSnowing() {
        return snow;
    }

    public void setSnow(boolean snow) {
        this.snow = snow;
        this.markDirty();
    }

    public boolean isSandEnabled() {
        return sand;
    }

    public void setSand(boolean sand) {
        this.sand = sand;
        this.markDirty();
    }

    public boolean isFoggy() {
        return fog;
    }

    public void setFog(boolean fog) {
        if (this.fog != fog) {
            this.fog = fog;
            this.markDirty();
        }
    }

    public boolean hasHud() {
        return hud;
    }

    public void setHud(boolean hud) {
        if (this.hud != hud) {
            this.hud = hud;
            this.markDirty();
        }
    }

    public TimeOfDay getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(TimeOfDay timeOfDay) {
        if (this.timeOfDay != timeOfDay) {
            this.timeOfDay = timeOfDay;
            this.markDirty();
        }
    }

    @Override
    public void readFromNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        this.setSpeed(nbtCompound.getInt("Speed"));
        this.setTime(nbtCompound.getInt("Time"));
        this.setSnow(nbtCompound.getBoolean("Snow"));
        this.setSand(nbtCompound.getBoolean("Sand"));
        this.setFog(nbtCompound.getBoolean("Fog"));
        this.setHud(nbtCompound.getBoolean("Hud"));
        this.setTimeOfDay(TimeOfDay.valueOf(nbtCompound.getString("TimeOfDay")));
    }

    @Override
    public void writeToNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        nbtCompound.putInt("Speed", speed);
        nbtCompound.putInt("Time", time);
        nbtCompound.putBoolean("Snow", snow);
        nbtCompound.putBoolean("Sand", sand);
        nbtCompound.putBoolean("Fog", fog);
        nbtCompound.putBoolean("Hud", hud);
        nbtCompound.putString("TimeOfDay", timeOfDay.name());
    }

    @Override
    public void clientTick() {
        tickTime();
    }

    private void tickTime() {
        if (speed > 0) {
            time++;
        } else {
            time = 0;
        }
    }

    @Override
    public void serverTick() {
        tickTime();
        if (this.needsSync) {
            ServerLevel serverWorld = (ServerLevel) world;
            serverWorld.setDayTime(timeOfDay.time);
        }

        // 每秒同步一次（减少网络占用）
        if (this.needsSync && this.world.getGameTime() % 60 == 0) {
            this.sync();
        }
    }

    public void reset() {
        this.snow = true;
        this.sand = true;
        this.fog = true;
        this.hud = true;
        this.speed = 130;
        this.time = 0;
        this.sync();
    }

    public enum TimeOfDay implements StringRepresentable {
        DAY(1000),
        NOON(6000),
        NIGHT(13000),
        MIDNIGHT(18000),
        SUNDOWN(12800);

        public final int time;

        TimeOfDay(int time) {
            this.time = time;
        }

        @Override
        public String getSerializedName() {
            return this.name();
        }
    }

}
