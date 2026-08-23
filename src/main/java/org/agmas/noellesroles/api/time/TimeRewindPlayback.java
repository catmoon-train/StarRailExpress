/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.api.time;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.TimeRewindVisualS2CPacket;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server-side playback controller for visually moving a player into a snapshot.
 */
final class TimeRewindPlayback {
    private static final ResourceLocation PLAYBACK_ID = Noellesroles.id("smooth_playback");
    private static final Map<UUID, ActiveRewind> ACTIVE = new ConcurrentHashMap<>();
    private static boolean initialized;

    private TimeRewindPlayback() {
    }

    static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(TimeRewindPlayback::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    static boolean begin(ServerPlayer player, TimeRewindSnapshot snapshot, int durationTicks,
            Consumer<TimeRewindResult> completion) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("smooth rewind must start on the server thread");
        }
        if (!player.getUUID().equals(snapshot.playerId()) || ACTIVE.containsKey(player.getUUID())) {
            return false;
        }
        int duration = Mth.clamp(durationTicks, 1, 20 * 30);
        ActiveRewind active = new ActiveRewind(player.getUUID(), snapshot, player.position(),
                player.getYRot(), player.getXRot(), duration, completion);
        ACTIVE.put(player.getUUID(), active);

        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        sendVisual(player, duration + 8);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 1.0f, 1.35f);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        return true;
    }

    static boolean cancel(ServerPlayer player) {
        ActiveRewind removed = ACTIVE.remove(player.getUUID());
        if (removed == null) {
            return false;
        }
        player.setDeltaMovement(Vec3.ZERO);
        sendVisual(player, 0);
        return true;
    }

    static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    static int activeCount() {
        return ACTIVE.size();
    }

    static void playVisual(ServerPlayer player, int durationTicks) {
        sendVisual(player, Mth.clamp(durationTicks, 0, 20 * 60));
    }

    private static void tick(MinecraftServer server) {
        for (ActiveRewind active : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(active.playerId);
            if (player == null || player.hasDisconnected()) {
                if (ACTIVE.remove(active.playerId, active)) {
                    complete(active, new TimeRewindResult(0, java.util.List.of(
                            new TimeRewindResult.Failure("playback", PLAYBACK_ID,
                                    "player disconnected during rewind"))));
                }
                continue;
            }
            tickPlayer(player, active);
        }
    }

    private static void tickPlayer(ServerPlayer player, ActiveRewind active) {
        active.elapsed++;
        float linear = Mth.clamp((float) active.elapsed / active.duration, 0.0f, 1.0f);
        float eased = smootherStep(linear);
        if (!player.hasEffect(MobEffects.INVISIBILITY))
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false, false));
        if (!player.hasEffect(ModEffects.INVINCIBLE))
            player.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, -1, 0, false, false, false));
        if (!player.hasEffect(ModEffects.SAFE_TIME))
            player.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, -1, 0, false, false, false));
        if (!player.hasEffect(ModEffects.MOVE_BANED))
            player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, -1, 0, false, false, false));
        if (!player.hasEffect(ModEffects.TURN_BANED))
            player.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, -1, 0, false, false, false));
        if (!player.hasEffect(ModEffects.SKIN_MASK))
            player.addEffect(new MobEffectInstance(ModEffects.SKIN_MASK, -1, 0, false, false, false));
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;

        if (player.level().dimension().equals(active.snapshot.dimension())) {
            Vec3 end = active.snapshot.position();
            double x = Mth.lerp(eased, active.start.x, end.x);
            double y = Mth.lerp(eased, active.start.y, end.y);
            double z = Mth.lerp(eased, active.start.z, end.z);
            float yRot = active.startYRot
                    + Mth.wrapDegrees(active.snapshot.yRot() - active.startYRot) * eased;
            float xRot = Mth.lerp(eased, active.startXRot, active.snapshot.xRot());
            player.teleportTo(x, y, z);
            player.setYRot(yRot);
            player.setYHeadRot(yRot);
            player.setXRot(xRot);
        }

        if ((active.elapsed & 1) == 0) {
            spawnTrail(player, linear);
        }
        if (active.elapsed >= active.duration && ACTIVE.remove(active.playerId, active)) {
            TimeRewindResult result = TimeRewind.restore(player, active.snapshot);
            player.setDeltaMovement(Vec3.ZERO);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.2f, 1.65f);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0, player.getZ(), 36,
                    0.65, 1.0, 0.65, 0.07);
            player.removeEffect(ModEffects.MOVE_BANED);
            player.removeEffect(ModEffects.TURN_BANED);
            player.removeEffect(ModEffects.SKIN_MASK);
            player.removeEffect(ModEffects.INVINCIBLE);
            player.removeEffect(ModEffects.SAFE_TIME);
            player.removeEffect(MobEffects.INVISIBILITY);
            complete(active, result);
        }
    }

    private static void spawnTrail(ServerPlayer player, float progress) {
        ServerLevel level = player.serverLevel();
        double radius = 0.35 + 0.75 * Math.sin(progress * Math.PI);
        double angle = progress * Math.PI * 12.0;
        double px = player.getX() + Math.cos(angle) * radius;
        double pz = player.getZ() + Math.sin(angle) * radius;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, px, player.getY() + 1.0, pz,
                5, 0.18, 0.45, 0.18, 0.015);
        level.sendParticles(new DustColorTransitionOptions(
                new Vector3f(0.15f, 0.95f, 1.0f),
                new Vector3f(0.72f, 0.16f, 1.0f), 1.35f),
                player.getX(), player.getY() + 0.9, player.getZ(),
                3, radius * 0.35, 0.65, radius * 0.35, 0.0);
    }

    private static void sendVisual(ServerPlayer player, int durationTicks) {
        if (ServerPlayNetworking.canSend(player, TimeRewindVisualS2CPacket.ID)) {
            ServerPlayNetworking.send(player, new TimeRewindVisualS2CPacket(durationTicks));
        }
    }

    private static float smootherStep(float value) {
        return value * value * value * (value * (value * 6.0f - 15.0f) + 10.0f);
    }

    private static void complete(ActiveRewind active, TimeRewindResult result) {
        if (active.completion == null) {
            return;
        }
        try {
            active.completion.accept(result);
        } catch (RuntimeException exception) {
            Noellesroles.LOGGER.error("Time rewind completion callback failed for {}",
                    active.playerId, exception);
        }
    }

    private static final class ActiveRewind {
        private final UUID playerId;
        private final TimeRewindSnapshot snapshot;
        private final Vec3 start;
        private final float startYRot;
        private final float startXRot;
        private final int duration;
        private final Consumer<TimeRewindResult> completion;
        private int elapsed;

        private ActiveRewind(UUID playerId, TimeRewindSnapshot snapshot, Vec3 start,
                float startYRot, float startXRot, int duration,
                Consumer<TimeRewindResult> completion) {
            this.playerId = playerId;
            this.snapshot = snapshot;
            this.start = start;
            this.startYRot = startYRot;
            this.startXRot = startXRot;
            this.duration = duration;
            this.completion = completion;
        }
    }
}
