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

package io.wifi.starrailexpress.api;

import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

/**
 * @author wifi_left, canyuesama
 */
public interface RoleComponent extends AutoSyncedComponent {
    Player getPlayer();

    void init();

    void clear();

    @Override
    default boolean shouldSyncWith(ServerPlayer player) {
        return this.getPlayer() == player;
    }

    void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    default void writeToSyncNbtWithPlayer(CompoundTag tag, HolderLookup.Provider registryLookup,
            ServerPlayer recipient) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    default void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        this.writeToSyncNbtWithPlayer(tag, buf.registryAccess(), recipient);
        buf.writeNbt(tag);
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    default void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            this.readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    /**
     * 一般情况下请不要使用这个方法。这个方法会让玩家NBT长度暴增，极有可能导致玩家无法进入游戏。更建议使用writeToSyncNbt。
     */
    @Override
    default void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    
    /**
     * 一般情况下无需用到此方法。
     */
    @Override
    default void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
