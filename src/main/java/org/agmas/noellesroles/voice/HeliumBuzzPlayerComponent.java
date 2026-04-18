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

    public HeliumBuzzPlayerComponent(Player player) {
        this.player = player;
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
            }
        }
    }

    /**
     * 同步给所有玩家，以便其他玩家能听到变声效果
     */
    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(@NotNull net.minecraft.network.RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(this.ticksRemaining);
        buf.writeByte(this.amplifier);
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
