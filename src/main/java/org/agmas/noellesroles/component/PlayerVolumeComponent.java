package org.agmas.noellesroles.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.client.utils.ModSoundManager;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 玩家声音组件
 *
 * 功能：
 * - 冷却时间管理（自动递减）
 * - 自动同步到客户端（修改声音）
 */
public class PlayerVolumeComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<PlayerVolumeComponent> KEY = ModComponents.VOLUME;

    // 持有该组件的玩家
    public final Player player;

    // 主播模式
    public boolean vtMode = false;

    // 声音剩余时间
    public int left_time = -1;

    // 设定音量
    public float volume = -1;

    public int client_status = -1;

    public SoundEvent duringSound = null;
    public int duringSoundPlayInterval = -1;

    /**
     * 构造函数
     */
    public PlayerVolumeComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.volume = -1;
        this.left_time = -1;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 设置声音时间
     * 
     */
    public void setVolume(int ticks, float volume) {
        this.left_time = ticks;
        this.volume = volume;
        this.sync();
    }

    /**
     * 设置声音时间
     * 
     */
    public void setVolume(int ticks, float volume, SoundEvent sound, int soundInterval) {
        this.left_time = ticks;
        this.volume = volume;
        this.duringSound = sound;
        this.duringSoundPlayInterval = soundInterval;
        this.sync();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 服务端每 tick 减少冷却时间
        if (this.left_time > 0) {
            this.left_time--;
            // 每秒同步一次（而不是每 tick），减少网络压力
            if (this.left_time % 20 == 0 || this.left_time == 0) {
                this.sync();
                this.left_time = -1;
                this.volume = -1;
            }
        }
    }

    @Override
    public void clientTick() {
        // 客户端也进行冷却计算（用于预测显示）
        if (this.left_time >= 1) {
            this.left_time--;
        }
        if (this.left_time > 0) {
            this.clientSetVolume();
            if (this.left_time > 0) {
                if (this.duringSoundPlayInterval >= 0) {
                    if (this.left_time % this.duringSoundPlayInterval == 0 && this.duringSound != null) {
                        this.player.playSound(this.duringSound);
                    }
                }
            }
        }
        if (this.client_status >= 0 && this.left_time <= 0) {
            this.client_status = -1;
            clientSetVolume();
            // this.left_time = -1;
        }
    }

    // ==================== NBT 序列化 ====================

    public void clientSetVolume() {
        // Noellesroles.LOGGER.info("Volume:" + this.volume);
        if (this.volume < 0) {
            ModSoundManager.resetGameSoundLevel();
        } else {
            ModSoundManager.setGameSoundLevel(volume);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("left_time", this.left_time);
        tag.putFloat("volume", this.volume);
        tag.putInt("during_sound_interval", this.duringSoundPlayInterval);
        tag.putBoolean("vtMode", this.vtMode);
        if (duringSound != null) {
            tag.putString("during_sound", duringSound.getLocation().toString());
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.vtMode = tag.contains("vtMode") ? tag.getBoolean("vtMode") : false;
        this.left_time = tag.contains("left_time") ? tag.getInt("left_time") : -1;
        this.duringSoundPlayInterval = tag.contains("during_sound_interval") ? tag.getInt("during_sound_interval")
                : -1;
        String during_sound = tag.contains("during_sound") ? tag.getString("during_sound") : null;

        if (during_sound != null) {
            var soundLocation = ResourceLocation.tryParse(during_sound);
            if (soundLocation != null) {
                this.duringSound = BuiltInRegistries.SOUND_EVENT.get(soundLocation);
            }
        }
        this.volume = tag.contains("volume") ? tag.getFloat("volume") : -1;
        if (this.player.level().isClientSide() && this.left_time > 0) {
            this.client_status = 1;
        }
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}