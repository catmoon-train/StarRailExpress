package io.wifi.starrailexpress.api.data;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家职业NBT数据
 * 
 * @param tag
 * @param registryLookup
 */
public interface RoleData {
    @Nullable
    public static RoleData getNullable(Player player) {
        return SRERoleDataPlayerComponent.KEY.get(player).roleData;
    }

    @Nullable
    public static Optional<RoleData> getOptional(Player player) {
        return Optional.ofNullable(SRERoleDataPlayerComponent.KEY.get(player).roleData);
    }

    Player getPlayer();

    default boolean shouldSyncWith(ServerPlayer player) {
        return this.getPlayer() == player;
    }

    void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);

    default void clientTick() {
    }

    default void serverTick() {
    }

    /**
     * 当玩家赋予该职业时触发
     */
    default void init() {
    }

    /**
     * 当玩家离开此职业时触发
     */
    default void clear() {
    }

    // 工具类方法，方便调用
    default String getStringTag(CompoundTag tag, String name, String defaultValue) {
        return RoleComponent.getStringTagOrDefault(tag, name, defaultValue);
    }

    default int getIntTag(CompoundTag tag, String name, int defaultValue) {
        return RoleComponent.getIntTagOrDefault(tag, name, defaultValue);
    }

    default Byte getByteTag(CompoundTag tag, String name, Byte defaultValue) {
        return RoleComponent.getByteTagOrDefault(tag, name, defaultValue);
    }

    default short getShortTag(CompoundTag tag, String name, short defaultValue) {
        return RoleComponent.getShortTagOrDefault(tag, name, defaultValue);
    }

    default long getLongTag(CompoundTag tag, String name, long defaultValue) {
        return RoleComponent.getLongTagOrDefault(tag, name, defaultValue);
    }

    default double getDoubleTag(CompoundTag tag, String name, double defaultValue) {
        return RoleComponent.getDoubleTagOrDefault(tag, name, defaultValue);
    }

    default float getFloatTag(CompoundTag tag, String name, float defaultValue) {
        return RoleComponent.getFloatTagOrDefault(tag, name, defaultValue);
    }

    default boolean getBooleanTag(CompoundTag tag, String name, boolean defaultValue) {
        return RoleComponent.getBooleanTagOrDefault(tag, name, defaultValue);
    }
}
