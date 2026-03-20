
package org.agmas.noellesroles.roles.manipulator;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.joml.Math;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * 操纵师组件
 */
public class ManipulatorPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<ManipulatorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator"),
            ManipulatorPlayerComponent.class);

    @Override
    public Player getPlayer() {
        return player;
    }

    public static final int CONTROL_DURATION = 30 * 20;

    public static final int CONTROL_COOLDOWN = 60 * 20;

    // ==================== 状态变量 ====================

    private final Player player;

    public UUID target;

    public boolean isControlling;

    public int cooldown;

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    public ManipulatorPlayerComponent(Player player) {
        this.player = player;
        this.target = null;
        this.isControlling = false;
        this.cooldown = 0;
    }

    @Override
    public void init() {
        this.target = null;
        this.isControlling = false;
        this.cooldown = 0;

        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    public void clearAll() {
        this.target = null;
        this.isControlling = false;
        this.cooldown = 0;

        this.sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public boolean canUseAbility() {
        return !isControlling;
    }

    /**
     * 
     * @param targetUuid
     */
    public void setTarget(UUID targetUuid) {
        if (!canUseAbility())
            return;
        if (!(player instanceof ServerPlayer sp))
            return;
        // player.displayClientMessage(Component.literal("test # start"), true);
        stopControl(false);
        Player targetPlayer = player.level().getPlayerByUUID(targetUuid);
        if (targetPlayer == null || !(targetPlayer instanceof ServerPlayer))
            return;

        if (targetUuid.equals(player.getUUID()))
            return;

        if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer))
            return;
        isControlling = true;
        float percent = 0.5f;
        int alivePlayerCount = 0;
        var players = sp.level().players();
        int playerCount = players.size();
        for (var spp : players) {
            if (GameUtils.isPlayerAliveAndSurvival(spp)) {
                alivePlayerCount++;
            }
        }
        percent = Math.clamp(0.5f, 1f, (float) alivePlayerCount / playerCount);
        int controlTime = (int) ((float) CONTROL_DURATION * percent);
        this.target = targetUuid;
        final var inControlCCA = InControlCCA.KEY.get(targetPlayer);
        inControlCCA.isControlling = true;
        inControlCCA.controlTimer = controlTime;
        inControlCCA.controller = player.getUUID();
        inControlCCA.sync();
        this.sync();
        // this.cooldown = CONTROL_COOLDOWN;

    }

    /**
     * 停止操控
     * 
     * @param timeout
     */
    public void stopControl(boolean timeout) {

        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        if (target != null) {
            Player targetPlayer = player.level().getPlayerByUUID(target);
            if (targetPlayer != null) {
                InControlCCA.KEY.get(targetPlayer).stopControl();
            }
        } else {
            isControlling = false;
            target = null;
            return;
        }
        isControlling = false;
        target = null;
        // 设置冷却
        // cooldown = CONTROL_COOLDOWN;

        // 发送消息
        if (timeout) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.manipulator.control_timeout")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        } else {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.manipulator.control_stopped")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        // 播放音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        this.sync();
    }

    public float getControlSeconds() {
        Player targetPlayer = player.level().getPlayerByUUID(target);
        if (targetPlayer != null) {
            return InControlCCA.KEY.get(targetPlayer).controlTimer / 20.0f;
        }
        return 0f;
    }

    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    @Override
    public void serverTick() {

        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            if (this.isControlling) {
                this.stopControl(false);
            }
            return;
        }

        if (isControlling) {
            if (target != null) {
                long currentTime = player.level().getGameTime();
                if (currentTime % 20 == 0) {
                    if ((player instanceof ServerPlayer serverPlayer)) {
                        Player targetPlayer = serverPlayer.level().getPlayerByUUID(target);
                        if (targetPlayer != null) {
                            if (!InControlCCA.KEY.get(targetPlayer).isControlling) {
                                stopControl(false);
                            }
                        } else {
                            stopControl(false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.target != null) {
            tag.putUUID("target", this.target);
        }
        tag.putBoolean("isControlling", this.isControlling);

    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : null;
        this.isControlling = tag.contains("isControlling") && tag.getBoolean("isControlling");
        // this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;

    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}