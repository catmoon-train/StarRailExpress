package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class NinjaPlayerComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<NinjaPlayerComponent> KEY = ModComponents.NINJA;

    private static final int ABILITY_COOLDOWN = 300 * 20;   // 300秒
    private static final int ABILITY_DURATION = 2 * 20 + 10; // 2.5秒
    private static final int BOUNTY_AMOUNT = 100;

    private final Player player;
    public int cooldown = 0;
    public int duration = 0;
    public boolean hasShield = false;
    public boolean shieldUsed = false;

    // HUD 辅助方法
    public boolean isOnCooldown() { return cooldown > 0; }
    public boolean isAbilityActive() { return duration > 0; }
    public float getCooldownSeconds() { return cooldown / 20.0f; }
    public float getDurationSeconds() { return duration / 20.0f; }

    public NinjaPlayerComponent(Player player) { this.player = player; }

    @Override public Player getPlayer() { return player; }

    @Override public void init() {
        this.cooldown = 0;
        this.duration = 0;
        this.hasShield = false;
        this.shieldUsed = false;
        sync();
    }

    @Override public void clear() { init(); }

    @Override public boolean shouldSyncWith(ServerPlayer target) { return this.player == target; }

    public void sync() { KEY.sync(this.player); }

    public boolean canUseAbility() {
        return cooldown <= 0 && duration <= 0 && !hasShield;
    }

    public boolean useAbility() {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;
        if (!canUseAbility()) {
            if (player instanceof ServerPlayer sp && cooldown > 0) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ninja.block_cooldown", (cooldown + 19) / 20)
                                .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.getRole(player.getUUID()) != ModRoles.NINJA) return false;

        this.hasShield = true;
        this.shieldUsed = false;
        this.duration = ABILITY_DURATION;
        // 注意：不设置 cooldown，冷却将在护盾结束后开始
        sync();

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.ninja.block_activate")
                            .withStyle(ChatFormatting.GREEN), true);
        }
        return true;
    }

    public boolean tryBlockDamage() {
        if (hasShield && !shieldUsed) {
            this.shieldUsed = true;
            this.hasShield = false;
            this.duration = 0;
            // 护盾被触发，立即开始冷却
            this.cooldown = ABILITY_COOLDOWN;
            sync();
            player.level().playSound(null, player.blockPosition(),
                    TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.PLAYERS, 1.0F, 0.8F);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ninja.block_success")
                                .withStyle(ChatFormatting.GOLD), true);
            }
            return true;
        }
        return false;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.getRole(player.getUUID()) != ModRoles.NINJA) return;
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) return;

        if (duration > 0) {
            duration--;
            if (duration <= 0) {
                // 护盾自然结束，开始冷却
                hasShield = false;
                shieldUsed = false;
                cooldown = ABILITY_COOLDOWN;
                sync();
            } else if (duration % 20 == 0) {
                sync();
            }
        }
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 20 == 0 || cooldown == 0) sync();
        }
    }

    public static void registerEvents() {
        OnPlayerKilledPlayer.EVENT.register((victim, killer, reason) -> {
            if (killer == null || victim == null) return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());
            if (gameWorld.getRole(killer.getUUID()) != ModRoles.NINJA) return;

            int lightLevel = killer.level().getBrightness(LightLayer.BLOCK, killer.blockPosition());
            boolean isDark = lightLevel <= 5;
            if (!isDark) return;

            if (killer instanceof ServerPlayer sp) {
                SREPlayerShopComponent shopComp = SREPlayerShopComponent.KEY.get(sp);
                shopComp.addToBalance(BOUNTY_AMOUNT);
                shopComp.sync();
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.ninja.bounty", BOUNTY_AMOUNT)
                                .withStyle(ChatFormatting.GOLD), true);
            }
        });

        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            NinjaPlayerComponent comp = KEY.get(victim);
            if (comp != null && comp.tryBlockDamage()) return false;
            return true;
        });
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", cooldown);
        tag.putInt("duration", duration);
        tag.putBoolean("hasShield", hasShield);
        tag.putBoolean("shieldUsed", shieldUsed);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        cooldown = tag.getInt("cooldown");
        duration = tag.getInt("duration");
        hasShield = tag.getBoolean("hasShield");
        shieldUsed = tag.getBoolean("shieldUsed");
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {}
}