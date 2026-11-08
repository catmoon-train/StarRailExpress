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

package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.DefaultStateSyncGate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class GhostStateComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<GhostStateComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("ghost_state"), GhostStateComponent.class);
    private final Player player;

    public boolean isGhost = false;
    /** 默认非幽灵态时不向观察者发包，见 {@link DefaultStateSyncGate}。 */
    private final DefaultStateSyncGate syncGate;

    public void sync() {
        GhostStateComponent.KEY.sync(player);
    }

    public GhostStateComponent(Player player) {
        this.player = player;
        this.syncGate = new DefaultStateSyncGate(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        // 幽灵态会让玩家在所有人眼里隐身（见 GhostStatePlayerRenderer），
        // 但「不是幽灵」这一默认态的包对观察者没有任何信息量：
        // CCA 在开始追踪 / 玩家登录时会对每个组件无脑 sync，本组件挂在所有玩家身上，
        // 不门控就是「玩家数 × 观察者数」个空包。
        return this.syncGate.shouldSyncWith(player, isDefaultSyncState());
    }

    /** 是否处于「不是幽灵」的默认态（此时空包对观察者没有任何意义）。 */
    public boolean isDefaultSyncState() {
        return !this.isGhost;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.isGhost = false;
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void writeToSyncNbtWithPlayer(CompoundTag tag, HolderLookup.Provider registryLookup,
            ServerPlayer recipient) {
        writeToSyncNbt(tag, registryLookup);
        // 只有这里能拿到 recipient：记录本次发出去的是默认态还是幽灵态，
        // 退出幽灵态后才能给「拿到过幽灵态」的观察者补发一次复位包。
        this.syncGate.recordSent(recipient, isDefaultSyncState());
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 默认非幽灵态写空包：CCA 开始追踪实体时会对全部组件自动同步，
        // 省掉默认字段能把这类包压到近乎空载。
        if (this.isGhost) {
            tag.putBoolean("isGhost", true);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isGhost = tag.getBoolean("isGhost");
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isGhost = tag.getBoolean("isGhost");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isGhost", this.isGhost);
    }

    public boolean isGhostState() {
        return this.isGhost;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.hasEffect(ModEffects.GHOST_STATE)) {
                GhostStateComponent ghostStateComponent = GhostStateComponent.KEY.get(serverPlayer);
                if (ghostStateComponent.isGhostState()) {
                    ghostStateComponent.clear();
                    ghostStateComponent.sync();
                    // sync() 已经补发过复位包，记录不再需要
                    ghostStateComponent.syncGate.clear();
                }
            }
        }
    }
}
