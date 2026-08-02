package io.wifi.starrailexpress.api.impl;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 示例：简单实例化RoleData类。
 * SimpleRoleData
 */
public class SimpleRoleData implements RoleData {

    final protected RoleDataContext ctx;

    public SimpleRoleData(RoleDataContext context) {
        this.ctx = context;
    }

    @Override
    public Player getPlayer() {
        return ctx.player();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
    }

    public void sync() {
        ctx.sync();
    }

    public void clientTick() {
    }

    public void serverTick() {
    }

    /**
     * 当玩家赋予该职业时触发
     */
    public void init() {
    }

    /**
     * 当玩家离开此职业时触发
     */
    public void clear() {
    }
}
