package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.ToggleInsaneSkillC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InsaneKillerPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<InsaneKillerPlayerComponent> KEY = ModComponents.INSANE_KILLER;
    public static boolean skipPD = false;
    private final Player player;

    public boolean isActive = false;
    public int deathState = 0;
    public int cooldown = 200;
    // public UUID target = null;
    public static Map<UUID, PlayerBodyEntity> playerBodyEntities = new HashMap<>();
    public static Map<UUID, Boolean> isPlayerBodyEntity = new HashMap<>();

    public InsaneKillerPlayerComponent(Player player) {
        this.player = player;
        this.isActive = false;
    }

    public void nearDeathTick() {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        List<Entity> entities = player.level().getEntities(player,
                player.getBoundingBox().inflate(2), (entity) -> {
                    return entity instanceof PlayerBodyEntity;
                });

        if (entities == null || entities.size() <= 0) {
            return;
        }
        // Noellesroles.LOGGER.info("" + entities.size());

        boolean canRevive = entities.stream().anyMatch((p) -> {
            if (p instanceof PlayerBodyEntity b) {
                if (b.getPlayerUuid().equals(player.getUUID())) {
                    return false;
                }
                if (b.distanceTo(player) <= 1.)
                    return true;
            }
            return false;
        });

        if (canRevive) {
            if (this.getDeathState() > 0) {
                this.revive();
            }
        }
    }

    public static void registerEvent() {

        AfterShieldAllowPlayerDeath.EVENT.register(((playerEntity, identifier) -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.get(playerEntity.level());
            if (gameWorldComponent.isRole(playerEntity,
                    ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
                if (!gameWorldComponent.isSkillAvailable) {
                    // player.displayClientMessage(
                    // Component.translatable("message.tip.skill_disabled").withStyle(ChatFormatting.RED),
                    // true);
                    return true;
                }
                InsaneKillerPlayerComponent insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY
                        .get(playerEntity);
                if (insaneKillerPlayerComponent.getDeathState() == 0) {
                    insaneKillerPlayerComponent.deathState = 20 * 60;
                    insaneKillerPlayerComponent.isActive = true;
                    if (playerEntity instanceof ServerPlayer sp) {
                        ServerPlayNetworking.send(sp, new ToggleInsaneSkillC2SPacket(true));
                    }
                    insaneKillerPlayerComponent.sync();
                    playerEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 99999, 4));
                    playerEntity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 99999, 4));
                    // insaneKillerPlayerComponent.sync();
                    playerEntity.stopRiding();
                    return false;
                }
            }
            return true;
        }));
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void revive() {
        this.deathState = -1;
        this.isActive = false;
        // this.sync();

        if (player instanceof ServerPlayer sp) {
            ServerPlayNetworking.send(sp, new ToggleInsaneSkillC2SPacket(false));
        }

        if (player instanceof ServerPlayer serverPlayer) {
            SRE.REPLAY_MANAGER.recordPlayerRevival(serverPlayer.getUUID(),
                    ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES);
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.insane_killer.revive").withStyle(ChatFormatting.GOLD), true);
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.insane_killer.revive").withStyle(ChatFormatting.GOLD), false);
            serverPlayer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            serverPlayer.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        SREPlayerShopComponent.KEY.get(player).addToBalance(100);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sync();
    }

    @Override
    public void init() {
        isActive = false;
        cooldown = 200;
        // Noellesroles.LOGGER.info("Trigger insane reset");
        deathState = 0;
        this.sync();
    }

    public boolean isUsedDeathAbility() {
        return deathState != 0;
    }

    public int getDeathState() {
        return deathState;
    }

    public boolean inNearDeath() {
        return deathState > 0;
    }

    @Override
    public void clear() {
        // Noellesroles.LOGGER.info("Trigger insane clear");
        this.init();
    }

    public void toggleAbility() {
        if (inNearDeath())
            return;
        if (isActive) {
            isActive = false;
            if (player instanceof ServerPlayer) {
                ServerPlayNetworking.send((ServerPlayer) player, new ToggleInsaneSkillC2SPacket(false));
            }
            cooldown = 45 * 20;
            // 发送取消激活的消息提示
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.insane_killer.ability_deactivated")
                    .withStyle(ChatFormatting.RED), true);

            // 播放取消激活的音效
            // player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            // SoundEvents.ALLAY_DEATH, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F,
            // 0.8F);
        } else {
            isActive = true;
            if (player instanceof ServerPlayer) {
                ServerPlayNetworking.send((ServerPlayer) player, new ToggleInsaneSkillC2SPacket(true));
            }
            // 发送激活的消息提示
            player.displayClientMessage(Component.translatable("message.noellesroles.insane_killer.ability_activated")
                    .withStyle(ChatFormatting.GREEN), true);
            //
            // 播放激活的音效
            // player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            // SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS,
            // 0.7F, 1.2F);
        }

        // if (cooldown > 0 && !isActive)
        // return;
        //
        // isActive = !isActive;
        // if (!isActive) {
        // cooldown = 30 * 20;
        // }
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return true;
    }

    @Override
    public void serverTick() {
        if (!SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES))
            return;
        if (cooldown > 0) {
            cooldown--;
            // if (cooldown == 0){
            //
            // }
            if (cooldown % 100 == 0)
                sync();
        }
        if (deathState > 0) {
            nearDeathTick();
            deathState--;
            if (deathState == 1) {
                GameUtils.killPlayer(player, true, null, Noellesroles.id("insane_killer_death"));
            }
            if (deathState % 100 == 0 || deathState == 1 || deathState == 0) {
                sync();
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isActive", isActive);
        tag.putInt("cooldown", cooldown);
        tag.putInt("death_state", deathState);
        // tag.putUUID("target", target);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        isActive = tag.contains("isActive") && tag.getBoolean("isActive");
        cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        deathState = tag.contains("death_state") ? tag.getInt("death_state") : 0;
        // target = tag.contains("target") ? tag.getUUID("target") : null;
    }

    @Override
    public void clientTick() {
        if (cooldown > 1) {
            cooldown--;
        }
        if (deathState > 0) {
            deathState--;
        }
    }

    // @Override
    // public void clientTick() {
    // final var player1 = Minecraft.getInstance().player;
    // if (!GameWorldComponent.KEY.get(player1.level()).isRole(player1,
    // ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES))
    // {
    // return;
    // }
    // if (isActive){
    // Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
    // }else {
    // Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
    // }
    //
    // }
}