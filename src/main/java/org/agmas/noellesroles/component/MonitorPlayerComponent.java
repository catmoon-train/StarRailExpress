package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import java.util.UUID;

/**
 * 监察员组件
 * 
 * 
 */
public class MonitorPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<MonitorPlayerComponent> KEY = ModComponents.MONITOR;

    private final Player player;

    public UUID markedTarget = null;

    public int cooldown = 0;

    /** 冷却时间 */
    public static final int COOLDOWN_TICKS = 60 * 20;

    public MonitorPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 标记目标玩家
     * 
     * @param target 目标玩家 UUID
     */
    public void markTarget(UUID target) {
        this.markedTarget = target;
        this.cooldown = COOLDOWN_TICKS;
        this.sync();
    }

    public boolean canUseAbility() {
        return cooldown <= 0;
    }

    public UUID getMarkedTarget() {
        return markedTarget;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.markedTarget = null;
        this.cooldown = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    @Override
    public void serverTick() {
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 20 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }
        if (this.player.level() == null) {
            return;
        }
        // 检查目标是否存活，如果死亡则清除标记
        if (this.markedTarget != null) {
            if (!this.player.level().isClientSide()) {
                Player targetPlayer = this.player.getServer().getPlayerList().getPlayer(this.markedTarget);
                if (targetPlayer == null || !GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                    // 目标不存在或已死亡，清除标记
                    this.markedTarget = null;
                    Component targetPlayerName = Component.translatable("gui.noellesroles.monitor.unknown");
                    if (targetPlayer != null) {
                        targetPlayerName = targetPlayer.getDisplayName();
                    }
                    this.player.displayClientMessage(
                            Component
                                    .translatable("gui.noellesroles.monitor.target_died",
                                            Component.literal("").append(targetPlayerName)
                                                    .withStyle(ChatFormatting.GOLD))
                                    .withStyle(ChatFormatting.RED),
                            true);
                    this.sync();
                }
            }
        }
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            KEY.sync(this.player);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.markedTarget != null) {
            tag.putUUID("markedTarget", this.markedTarget);
        }
        tag.putInt("cooldown", this.cooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.markedTarget = tag.contains("markedTarget") ? tag.getUUID("markedTarget") : null;
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}