package org.agmas.noellesroles.effects;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.packet.CanMoveInTimeStopS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TimeStopEffect extends MobEffect {
    public static List<UUID> canMovePlayers = new ArrayList<>();
    public static Map<UUID, Vec3> clientPositions = new java.util.HashMap<>();
    public static int freezeTime = 0;
    public static int freezeStatedTime = 0;
    public static int freezeMaxTime = 0;

    public TimeStopEffect() {
        super(MobEffectCategory.NEUTRAL, Color.white.getRGB());
    }

    @Override
    public boolean isEnabled(FeatureFlagSet featureFlagSet) {
        return true;
    }

    public static void tryTriggerStart(ServerPlayer serverPlayer, int time, Component displaySkillTitle) {
        if (serverPlayer.hasEffect(ModEffects.TIME_STOP))
            return;
        triggerStart(serverPlayer, time, displaySkillTitle);
    }

    public static void triggerStart(ServerPlayer serverPlayer, int time, Component displaySkillTitle) {
        canMovePlayers.clear();
        canMovePlayers.add(serverPlayer.getUUID());
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
        var broadcastMessage = displaySkillTitle;

        SREGameTimeComponent gameTimeComponent = SREGameTimeComponent.KEY.get(serverPlayer.level());
        gameTimeComponent.setTime(gameTimeComponent.time + time);
        ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Time_Stop"));
        serverPlayer.serverLevel().players().forEach(
                player -> {
                    player.stopUsingItem();
                    if (player.isSleeping()) {
                        player.stopSleeping();
                    }
                    player.playNotifySound(NRSounds.TIME_STOP, SoundSource.PLAYERS, 1.0F, 1.0F);

                    BroadcastCommand.BroadcastMessage(player, broadcastMessage);

                    if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                        if (WorldModifierComponent.KEY.get(player.level()).isModifier(player,
                                SEModifiers.SPLIT_PERSONALITY)) {

                        } else {
                            canMovePlayers.add(player.getUUID());
                        }
                    } else {
                        if (gameWorldComponent.isRole(player, ModRoles.MAID_SAKUYA)) {
                            canMovePlayers.add(player.getUUID());
                        }
                        // if (gameWorldComponent.isRole(player, ModRoles.JOJO)) {
                        // canMovePlayers.add(player.getUUID());
                        // }
                        if (gameWorldComponent.isRole(player, ModRoles.CLOCKMAKER)) {
                            canMovePlayers.add(player.getUUID());
                        }
                    }
                });
        serverPlayer.serverLevel().players().forEach(
                player -> {
                    player.addEffect(new MobEffectInstance((ModEffects.TIME_STOP), time, 0, false, false, false));
                    ServerPlayNetworking.send(player, new CanMoveInTimeStopS2CPacket(canMovePlayers, time));
                });
        MinecraftServer server = serverPlayer.getServer();
        ServerTickRateManager serverTickRateManager = server.tickRateManager();

        freezeTime = time;
        serverTickRateManager.setFrozen(true);
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int i) {
        return super.applyEffectTick(livingEntity, i);
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
        canMovePlayers.clear();
        clientPositions.clear();

    }

    public static int effectStatedTime = 0;

    @Override
    public void onEffectStarted(LivingEntity livingEntity, int i) {
        super.onEffectStarted(livingEntity, i);
    }

}
