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
import net.minecraft.nbt.Tag;
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

    default void writeToRewindNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
    }
    
    default void readFromRewindNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
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

    // 工具类方法，方便调用
    default String getStringTag(CompoundTag tag, String name, String defaultValue) {
        return getStringTagOrDefault(tag, name, defaultValue);
    }

    default int getIntTag(CompoundTag tag, String name, int defaultValue) {
        return getIntTagOrDefault(tag, name, defaultValue);
    }

    default Byte getByteTag(CompoundTag tag, String name, Byte defaultValue) {
        return getByteTagOrDefault(tag, name, defaultValue);
    }

    default short getShortTag(CompoundTag tag, String name, short defaultValue) {
        return getShortTagOrDefault(tag, name, defaultValue);
    }

    default long getLongTag(CompoundTag tag, String name, long defaultValue) {
        return getLongTagOrDefault(tag, name, defaultValue);
    }

    default double getDoubleTag(CompoundTag tag, String name, double defaultValue) {
        return getDoubleTagOrDefault(tag, name, defaultValue);
    }

    default float getFloatTag(CompoundTag tag, String name, float defaultValue) {
        return getFloatTagOrDefault(tag, name, defaultValue);
    }

    default boolean getBooleanTag(CompoundTag tag, String name, boolean defaultValue) {
        return getBooleanTagOrDefault(tag, name, defaultValue);
    }

    // 静态方法，实现逻辑
    public static String getStringTagOrDefault(CompoundTag tag, String name, String defaultValue) {
        if (tag.contains(name, Tag.TAG_STRING)) {
            return tag.getString(name);
        }
        return defaultValue;
    }

    public static int getIntTagOrDefault(CompoundTag tag, String name, int defaultValue) {
        if (tag.contains(name, Tag.TAG_INT)) {
            return tag.getInt(name);
        }
        return defaultValue;
    }

    public static Byte getByteTagOrDefault(CompoundTag tag, String name, Byte defaultValue) {
        if (tag.contains(name, Tag.TAG_BYTE)) {
            return tag.getByte(name);
        }
        return defaultValue;
    }

    public static short getShortTagOrDefault(CompoundTag tag, String name, short defaultValue) {
        if (tag.contains(name, Tag.TAG_SHORT)) {
            return tag.getShort(name);
        }
        return defaultValue;
    }

    public static long getLongTagOrDefault(CompoundTag tag, String name, long defaultValue) {
        if (tag.contains(name, Tag.TAG_LONG)) {
            return tag.getLong(name);
        }
        return defaultValue;
    }

    public static double getDoubleTagOrDefault(CompoundTag tag, String name, double defaultValue) {
        if (tag.contains(name, Tag.TAG_DOUBLE)) {
            return tag.getDouble(name);
        }
        return defaultValue;
    }

    public static float getFloatTagOrDefault(CompoundTag tag, String name, float defaultValue) {
        if (tag.contains(name, Tag.TAG_FLOAT)) {
            return tag.getFloat(name);
        }
        return defaultValue;
    }

    public static boolean getBooleanTagOrDefault(CompoundTag tag, String name, boolean defaultValue) {
        if (tag.contains(name)) {
            return tag.getBoolean(name);
        }
        return defaultValue;
    }
}
