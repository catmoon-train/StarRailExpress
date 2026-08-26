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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

public class DiscMasterRoleData extends SimpleRoleData {
    /** 购买任意唱片后，全部唱片进入的购买冷却（2 分钟 = 120 * 20 tick） */
    public static final int MUSIC_COOLDOWN = 120 * 20;

    /** 当前购买冷却剩余时间（tick） */
    public int musicCooldown = 0;

    public DiscMasterRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        this.musicCooldown = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.musicCooldown = 0;
        this.sync();
    }

    /**
     * 播放指定唱片对应的音乐，并设置全唱片购买冷却。
     * @return 是否成功播放（冷却中则失败）
     */
    public boolean playDisc(SoundEvent sound) {
        if (musicCooldown > 0) return false;
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.RECORDS, 4.0F, 1.0F);
        musicCooldown = MUSIC_COOLDOWN;
        this.sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (musicCooldown > 0) {
            musicCooldown--;
            if (musicCooldown % 20 == 0 || musicCooldown == 0) {
                this.sync();
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("musicCooldown", this.musicCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.musicCooldown = tag.contains("musicCooldown") ? tag.getInt("musicCooldown") : 0;
    }
}
