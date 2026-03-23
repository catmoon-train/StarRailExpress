package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class WatcherPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<WatcherPlayerComponent> KEY = ModComponents.WATCHER;
    /** 姿态切换冷却（60秒） */
    public static final int STANCE_SWITCH_COOLDOWN_TICKS = 60 * 20;
    /** 速度效果持续时间（tick） */
    private static final int SPEED_EFFECT_DURATION = 60;
    /** 速度效果刷新阈值（tick） */
    private static final int SPEED_REFRESH_THRESHOLD = 30;

    private final Player player;
    private boolean isCalm = true;
    private int cooldown = 0;
    private boolean shieldConsumed = false;

    public WatcherPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.isCalm = true;
        this.cooldown = 0;
        this.shieldConsumed = false;
        SREArmorPlayerComponent.KEY.get(player).giveArmor();
        applyCalmState();
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void setCooldown(int ticks) {
        this.cooldown = ticks;
        this.sync();
    }

    public int getCooldown() {
        return this.cooldown;
    }

    public boolean isInCalmStance() {
        return this.isCalm;
    }

    public void markShieldConsumed() {
        if (!this.shieldConsumed) {
            this.shieldConsumed = true;
            this.sync();
        }
    }

    public void toggleStance() {
        this.isCalm = !this.isCalm;
        setCooldown(STANCE_SWITCH_COOLDOWN_TICKS);
        if (this.isCalm) {
            applyCalmState();
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.watcher.stance_calm")
                            .withStyle(ChatFormatting.AQUA),
                    true);
        } else {
            applyAngryState();
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.watcher.stance_angry")
                            .withStyle(ChatFormatting.RED),
                    true);
        }
        this.sync();
    }

    private void applyCalmState() {
        var speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed != null && speed.getAmplifier() == 0 && speed.isAmbient()) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
        var armorComponent = SREArmorPlayerComponent.KEY.get(player);
        if (!shieldConsumed && armorComponent.getArmor() <= 0) {
            armorComponent.giveArmor();
        }
    }

    private void applyAngryState() {
        var armorComponent = SREArmorPlayerComponent.KEY.get(player);
        if (armorComponent.getArmor() > 0) {
            armorComponent.removeArmor(armorComponent.getArmor());
        }
        ensureAngrySpeed();
    }

    private void ensureAngrySpeed() {
        var speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed == null) {
            player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_EFFECT_DURATION, 0, true, false, true));
            return;
        }
        if (speed.getAmplifier() == 0 && speed.getDuration() < SPEED_REFRESH_THRESHOLD && speed.isAmbient()) {
            player.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_EFFECT_DURATION, 0, true, false, true));
        }
    }

    @Override
    public void clientTick() {
        if (cooldown > 1) {
            cooldown--;
        }
    }

    @Override
    public void serverTick() {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning() || !gameWorldComponent.isRole(player, ModRoles.WATCHER)) {
            return;
        }
        boolean shouldSync = false;
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 200 == 0 || cooldown == 0) {
                shouldSync = true;
            }
        }
        if (isCalm) {
            applyCalmState();
        } else {
            ensureAngrySpeed();
        }
        if (shouldSync) {
            sync();
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isCalm", this.isCalm);
        tag.putInt("cooldown", this.cooldown);
        tag.putBoolean("shieldConsumed", this.shieldConsumed);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isCalm = tag.getBoolean("isCalm");
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.shieldConsumed = tag.getBoolean("shieldConsumed");
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
