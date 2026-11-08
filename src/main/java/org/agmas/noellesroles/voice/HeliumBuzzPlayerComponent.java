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

package org.agmas.noellesroles.voice;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 氦气变声效果组件
 * 用于在玩家语音中实现氦气变声效果
 */
public class HeliumBuzzPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {

    public static final ComponentKey<HeliumBuzzPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("helium_buzz"),
            HeliumBuzzPlayerComponent.class);

    private final Player player;

    /** 剩余变声时间（ticks） */
    private int ticksRemaining = 0;

    /** 变声强度（0=无效果，1=标准氦气） */
    private byte amplifier = 0;

    /** 默认无效果态时不向观察者发包，见 {@link io.wifi.starrailexpress.cca.DefaultStateSyncGate}。 */
    private final io.wifi.starrailexpress.cca.DefaultStateSyncGate syncGate;

    public HeliumBuzzPlayerComponent(Player player) {
        this.player = player;
        this.syncGate = new io.wifi.starrailexpress.cca.DefaultStateSyncGate(player);
    }

    /**
     * 应用氦气变声效果
     * @param ticks 持续时间（ticks）
     * @param amplifier 强度（目前未使用，保留兼容性）
     */
    public void apply(int ticks, int amplifier) {
        this.ticksRemaining = Math.max(0, ticks);
        this.amplifier = (byte) Math.max(0, Math.min(127, amplifier));
        KEY.sync(this.player);
    }

    /**
     * 清除氦气变声效果
     */
    public void clear() {
        if (this.ticksRemaining == 0 && this.amplifier == 0) {
            return;
        }
        this.ticksRemaining = 0;
        this.amplifier = 0;
        KEY.sync(this.player);
        // sync() 已经补发过复位包，记录不再需要
        this.syncGate.clear();
    }

    /**
     * 检查效果是否激活
     */
    public boolean isActive() {
        return (this.ticksRemaining > 0);
    }

    /**
     * 获取强度
     */
    public int getAmplifier() {
        return this.amplifier & 0xFF;
    }

    /**
     * 获取剩余时间
     */
    public int getTicksRemaining() {
        return this.ticksRemaining;
    }

    @Override
    public void serverTick() {
        if (this.ticksRemaining > 0) {
            this.ticksRemaining--;
            if (this.ticksRemaining == 0) {
                this.amplifier = 0;
                KEY.sync(this.player);
                // sync() 已经补发过复位包，记录不再需要
                this.syncGate.clear();
            }
        }
    }

    /**
     * 同步给所有玩家，以便其他玩家能听到变声效果。
     *
     * <p>
     * 但默认无效果态（{@code ticksRemaining == 0 && amplifier == 0}）的包对观察者没有信息量：
     * CCA 在开始追踪 / 玩家登录时会对每个组件无脑 sync，本组件挂在所有玩家身上，
     * 不门控就是「玩家数 × 观察者数」个空包。因此默认态只同步给本人与
     * 「拿到过非默认数据」的观察者（见 {@link io.wifi.starrailexpress.cca.DefaultStateSyncGate}）。
     */
    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) {
        return this.syncGate.shouldSyncWith(recipient, isDefaultSyncState());
    }

    /** 是否处于「没有任何变声效果」的默认态（此时空包对观察者没有任何意义）。 */
    public boolean isDefaultSyncState() {
        return this.ticksRemaining == 0 && this.amplifier == 0;
    }

    @Override
    public void writeSyncPacket(@NotNull net.minecraft.network.RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(this.ticksRemaining);
        buf.writeByte(this.amplifier);
        // 只有这里能拿到 recipient：记录本次发出去的是默认态还是真实数据，
        // 效果结束后才能给「拿到过数据」的观察者补发一次复位包。
        this.syncGate.recordSent(recipient, isDefaultSyncState());
    }

    @Override
    public void applySyncPacket(@NotNull net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this.ticksRemaining = buf.readInt();
        this.amplifier = buf.readByte();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("t", this.ticksRemaining);
        tag.putByte("a", this.amplifier);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.ticksRemaining = tag.contains("t") ? tag.getInt("t") : 0;
        this.amplifier = tag.contains("a") ? tag.getByte("a") : 0;
    }
}
