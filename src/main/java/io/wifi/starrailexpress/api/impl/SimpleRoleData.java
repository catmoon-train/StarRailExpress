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

package io.wifi.starrailexpress.api.impl;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 示例：简单实例化RoleData类。
 * 因为每次变换职业都会创建新的实例，所以理论上不需要写init和clear来重置数据。
 * SimpleRoleData
 */
public abstract class SimpleRoleData implements RoleData {

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

    // @Override
    // public void writeToSyncNbt(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registryLookup) {
    // }

    // @Override
    // public void readFromSyncNbt(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registryLookup) {
    // }

    public void sync() {
        ctx.sync();
    }

    public void syncTo(ServerPlayer serverPlayer) {
        ctx.syncTo(serverPlayer);
    }

    @Override
    public void clientTick() {
    }

    @Override
    public void serverTick() {
    }

    /**
     * 当玩家赋予该职业时触发
     */
    @Override
    public void init() {
        // 不需要初始化各field也不需要同步，服务端和客户端均会使用该类初始值作为玩家变成此职业后的初始值。
    }

    /**
     * 当玩家离开此职业时触发
     */
    @Override
    public void clear() {
    }
}
