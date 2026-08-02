package io.wifi.starrailexpress.api.impl;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 示例：简单实例化RoleData类。
 * 因为每次变换职业都会创建新的实例，所以理论上不需要写init和clear来重置数据。
 * SimpleRoleData
 */
public class SimpleRoleData implements RoleData {

    final protected RoleDataContext ctx;
    final protected Player player;

    public SimpleRoleData(RoleDataContext context) {
        this.ctx = context;
        this.player = ctx.player();
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
