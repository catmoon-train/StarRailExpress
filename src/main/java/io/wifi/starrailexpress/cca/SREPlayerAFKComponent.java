package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class SREPlayerAFKComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerAFKComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("afk"),
            SREPlayerAFKComponent.class);
    private final Player player;
    private int afkTime = 0; // 挂机时间（刻）
    private int lastActionTime = 0; // 最后操作时间（刻）
    private boolean isAFK = false; // 是否挂机

    public SREPlayerAFKComponent(Player player) {
        this.player = player;
        this.lastActionTime = 0;
        this.afkTime = 0;
        this.isAFK = false;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public void init() {
        this.resetAFKTimer();
    }

    public void resetAFKTimer() {
        this.lastActionTime = 0;
        this.afkTime = 0;
        this.isAFK = false;
        this.sync();
    }

    public void updateActivity() {
        this.lastActionTime = 0;
        if (this.isAFK) {
            this.isAFK = false;
            this.afkTime = 0;
            this.sync();
        }
        if (getAFKProgress() >= 0.3) {
            this.isAFK = true;
        }

    }

    public void setAFKTime(int ticks) {
        this.afkTime = ticks;
        this.lastActionTime = ticks;
        // 根据设置的时间更新AFK状态
        int afkThreshold = SREConfig.instance().afkThresholdSeconds * 20; // 转换为ticks
        this.isAFK = this.afkTime >= afkThreshold;
        this.sync();
    }

    public int getAFKTime() {
        return this.afkTime;
    }

    public boolean isAFK() {
        return this.isAFK;
    }

    public boolean isSleepy() {
        int sleepyThreshold = SREConfig.instance().afkSleepySeconds * 20; // 转换为ticks
        return this.afkTime >= sleepyThreshold && !this.isAFK;
    }

    public boolean isWarning() {
        int warningThreshold = SREConfig.instance().afkWarningSeconds * 20; // 转换为ticks
        int afkThreshold = SREConfig.instance().afkThresholdSeconds * 20; // 转换为ticks
        return this.afkTime >= warningThreshold && this.afkTime < afkThreshold && !this.isAFK;
    }

    public float getAFKProgress() {
        int afkThreshold = SREConfig.instance().afkThresholdSeconds * 20; // 转换为ticks
        return (float) this.afkTime / afkThreshold;
    }

    public int tickR = 0;

    @Override
    public void serverTick() {
        ++tickR;
        if (!SRE.isPlayerInGame(this.player))
            return;

        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning())
            return;
        if (player.isSpectator()) {
            this.lastActionTime = 0;
        }
        this.lastActionTime++;
        this.afkTime = lastActionTime;

        // 检查是否达到挂机阈值
        int afkThreshold = SREConfig.instance().afkThresholdSeconds * 20; // 转换为ticks
        int warningThreshold = SREConfig.instance().afkWarningSeconds * 20; // 转换为ticks
        int sleepyThreshold = SREConfig.instance().afkSleepySeconds * 20; // 转换为ticks
        int deathThreshold = SREConfig.instance().afkDeathSeconds * 20; // 添加死亡阈值，转换为ticks
        if (tickR % 400 == 0) {// 20s 同步一次
            this.sync(); // 确保客户端同步进度
        }
        if (this.lastActionTime >= deathThreshold) {
            // 如果达到死亡阈值，直接杀死玩家
            GameUtils.killPlayer(this.player, true, null, SRE.id("death_afk"));
        } else if (this.lastActionTime >= afkThreshold && !this.isAFK) {
            this.isAFK = true;
            if (tickR % 400 == 0) { // 20s同步一次
                this.sync();
            }
        } else if (this.lastActionTime >= warningThreshold && this.lastActionTime < afkThreshold && !this.isAFK) {
            // 接近挂机阈值但还未达到
            if (tickR % 400 == 0) {// 20s 同步一次
                this.sync(); // 确保客户端同步进度
            }
        } else if (this.lastActionTime >= sleepyThreshold && this.lastActionTime < warningThreshold && !this.isAFK) {
            // 开始显示困倦效果
            if (tickR % 400 == 0) {// 20s 同步一次
                this.sync(); // 确保客户端同步进度
            }
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        this.afkTime = tag.getInt("afkTime");
        this.lastActionTime = tag.getInt("afkTime");
        this.isAFK = tag.getBoolean("isAFK");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putInt("afkTime", this.afkTime);
        tag.putBoolean("isAFK", this.isAFK);
    }

    @Override
    public void clientTick() {
        if (!SRE.isPlayerInGame(this.player))
            return;

        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning())
            return;
        if (player.isSpectator()) {
            this.lastActionTime = 0;
        }
        this.lastActionTime++;
        this.afkTime = this.lastActionTime;

    }

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {
    }
}