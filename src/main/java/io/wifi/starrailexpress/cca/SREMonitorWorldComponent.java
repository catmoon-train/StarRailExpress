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
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

public class SREMonitorWorldComponent implements AutoSyncedComponent, CommonTickingComponent {
    public static final ComponentKey<SREMonitorWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("monitor"),
            SREMonitorWorldComponent.class);
    public final Level world;
    public int brokenTime = 0;

    public SREMonitorWorldComponent(Level world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public boolean triggerBroken(boolean haveSound, int duration) {
        if (brokenTime > 0)
            return false;
        setBrokenTime(duration);
        if (haveSound) {
            if (this.world instanceof ServerLevel serverWorld) {
                for (ServerPlayer player : serverWorld.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        player.connection.send(new ClientboundSoundPacket(
                                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SHEEP_SHEAR),
                                SoundSource.PLAYERS,
                                player.getX(), player.getY(), player.getZ(), 100f, 1f, player.getRandom().nextLong()));
                    }
                }
            }
        }
        return true;
    }

    public void reset() {
        this.brokenTime = 0;
        sync();
    }

    @Override
    public void tick() {
        if (brokenTime > 0) {
            brokenTime--;
            if (brokenTime == 0) {
                if (!this.world.isClientSide) {
                    sync();
                }
            }
        }
    }

    public boolean isBroken() {
        return this.brokenTime > 0;
    }

    public int getBrokenTime() {
        return this.brokenTime;
    }

    public void addTime(int time) {
        this.setBrokenTime(this.brokenTime + time);
    }

    public void setBrokenTime(int time) {
        this.brokenTime = time;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("b_time", this.brokenTime);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("b_time"))
            this.brokenTime = tag.getInt("b_time");
        else
            this.brokenTime = 0;
    }
}