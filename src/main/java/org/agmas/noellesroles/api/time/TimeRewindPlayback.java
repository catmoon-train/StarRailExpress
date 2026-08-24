/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.api.time;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.TimeRewindVisualS2CPacket;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Server-side playback controller for visually moving a player into a snapshot.
 *
 * <p>
 * Every player gets one {@link PlaybackSession} that runs its rewinds strictly
 * in order: the head of the queue plays now, further requests are queued and
 * start as soon as the previous one finishes. All session mutation happens on
 * the Minecraft server thread; the shared collections are concurrent so that
 * read-only queries stay safe from any thread.
 */
public final class TimeRewindPlayback {
    private static final ResourceLocation PLAYBACK_ID = Noellesroles.id("smooth_playback");
    private static final Map<UUID, PlaybackSession> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private TimeRewindPlayback() {
    }

    static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(TimeRewindPlayback::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
        // 游戏开始/结束立即取消所有回溯，避免玩家卡在旁观模式或效果状态。
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) ->
                cancelAll(serverLevel.getServer()));
        OnGameStarted.EVENT.register(serverLevel -> cancelAll(serverLevel.getServer()));
    }

    /**
     * Starts a smooth rewind, or queues it when the player already has one
     * running. Returns false only when the snapshot belongs to another player.
     */
    static boolean begin(ServerPlayer player, TimeRewindSnapshot snapshot, int durationTicks,
            Consumer<TimeRewindResult> completion) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("smooth rewind must start on the server thread");
        }
        if (!player.getUUID().equals(snapshot.playerId())) {
            return false;
        }
        int duration = Mth.clamp(durationTicks, 1, 20 * 30);
        ActiveRewind active = new ActiveRewind(player.getUUID(), snapshot, duration, completion);
        PlaybackSession session = SESSIONS.computeIfAbsent(player.getUUID(), PlaybackSession::new);
        session.trackPreExistingEffects(player);
        session.pending.add(active);
        if (session.current == null) {
            startNext(player, session);
        }
        return true;
    }

    /**
     * Cancels the running rewind and drops the whole queue for that player.
     * The player is restored to the game mode they had before the rewind and
     * every completion callback receives a failure result.
     */
    static boolean cancel(ServerPlayer player) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("smooth rewind cancel must run on the server thread");
        }
        PlaybackSession session = SESSIONS.get(player.getUUID());
        return session != null && cancelSession(session, player, cancelledResult());
    }

    /** Cancels every running and queued rewind on the server. */
    static void cancelAll(MinecraftServer server) {
        if (server != null && !server.isSameThread()) {
            server.execute(() -> cancelAll(server));
            return;
        }
        for (PlaybackSession session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server == null ? null
                    : server.getPlayerList().getPlayer(session.playerId);
            cancelSession(session, player, cancelledResult());
        }
    }

    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static boolean isActive(UUID player) {
        return SESSIONS.containsKey(player);
    }

    static int activeCount() {
        return SESSIONS.size();
    }

    static void playVisual(ServerPlayer player, int durationTicks) {
        sendVisual(player, Mth.clamp(durationTicks, 0, 20 * 60));
    }

    private static TimeRewindResult cancelledResult() {
        return new TimeRewindResult(0, List.of(new TimeRewindResult.Failure("playback", PLAYBACK_ID,
                "cancelled during rewind")));
    }

    private static TimeRewindResult disconnectResult() {
        return new TimeRewindResult(0, List.of(new TimeRewindResult.Failure("playback", PLAYBACK_ID,
                "player disconnected during rewind")));
    }

    private static void tick(MinecraftServer server) {
        for (PlaybackSession session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null || player.hasDisconnected()) {
                if (SESSIONS.remove(session.playerId, session)) {
                    TimeRewindResult result = disconnectResult();
                    if (session.current != null) {
                        restoreGameModeOnDisconnect(player, session.current);
                        complete(session.current, result);
                    }
                    session.pending.forEach(pending -> complete(pending, result));
                    session.pending.clear();
                }
                continue;
            }
            if (session.current == null) {
                startNext(player, session);
                if (session.current == null) {
                    continue;
                }
            }
            tickCurrent(player, session);
        }
    }

    /** Best effort: a disconnecting player must not rejoin stuck in spectator. */
    private static void restoreGameModeOnDisconnect(ServerPlayer player, ActiveRewind active) {
        if (player == null || active.startGameType == null
                || player.gameMode.getGameModeForPlayer() == active.startGameType) {
            return;
        }
        try {
            player.setGameMode(active.startGameType);
        } catch (RuntimeException exception) {
            Noellesroles.LOGGER.debug("Could not restore game mode for disconnecting player {}",
                    active.playerId, exception);
        }
    }

    private static void tickCurrent(ServerPlayer player, PlaybackSession session) {
        ActiveRewind active = session.current;
        active.elapsed++;
        float linear = Mth.clamp((float) active.elapsed / active.duration, 0.0f, 1.0f);
        float eased = smootherStep(linear);
        addPlayerEffects(player);
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
        if (active.elapsed >= active.duration) {
            finishCurrent(player, session);
        }
    }

    private static void finishCurrent(ServerPlayer player, PlaybackSession session) {
        ActiveRewind active = session.current;
        TimeRewindResult result;
        boolean restoreSucceeded;
        try {
            result = TimeRewind.restore(player, active.snapshot);
            restoreSucceeded = true;
        } catch (RuntimeException exception) {
            Noellesroles.LOGGER.error("Time rewind restore failed for {}", active.playerId, exception);
            result = new TimeRewindResult(0, List.of(new TimeRewindResult.Failure("playback",
                    PLAYBACK_ID, describe(exception))));
            restoreSucceeded = false;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.2f, 1.65f);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(), 36,
                0.65, 1.0, 0.65, 0.07);
        // A successful restore already switches back to the snapshot's recorded
        // game mode. Only when the restore failed is the player still a
        // spectator, so fall back to the mode they had when this rewind began.
        if (!restoreSucceeded && active.startGameType != null
                && active.startGameType != GameType.SPECTATOR
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            player.setGameMode(active.startGameType);
        }
        sendVisual(player, 0);
        if (!restoreSucceeded) {
            removePlayerEffects(player, session);
        }
        complete(active, result);
        session.current = null;
        startNext(player, session);
    }

    /** Pulls the next queued rewind and activates it, or tears the session down. */
    private static void startNext(ServerPlayer player, PlaybackSession session) {
        ActiveRewind active = session.pending.poll();
        if (active == null) {
            session.current = null;
            SESSIONS.remove(session.playerId, session);
            removePlayerEffects(player, session);
            return;
        }
        // The slide origin is captured at activation time so queued rewinds start
        // from wherever the previous restore left the player.
        session.current = active;
        active.start = player.position();
        active.startYRot = player.getYRot();
        active.startXRot = player.getXRot();
        active.startGameType = player.gameMode.getGameModeForPlayer();

        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        // 旁观者模式下玩家不会受伤、不会成为目标，配合保护药水双重防护。
        if (active.startGameType != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        sendVisual(player, active.duration + 8);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 1.0f, 1.35f);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        addPlayerEffects(player);
    }

    /** Removes only the protection effects the session added itself. */
    private static void removePlayerEffects(ServerPlayer player, PlaybackSession session) {
        for (Holder<MobEffect> effect : PlaybackSession.PROTECTION_EFFECTS) {
            if (!session.preExisting.contains(effect)) {
                player.removeEffect(effect);
            }
        }
    }

    private static void addPlayerEffects(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.INVISIBILITY))
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0, false, false, false));
        if (!player.hasEffect(ModEffects.TIME_REWIND_MARK))
            player.addEffect(new MobEffectInstance(ModEffects.TIME_REWIND_MARK, -1, 0, false, false, false));
        if (!player.hasEffect(ModEffects.INVINCIBLE))
            player.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 600, 0, false, false, false));
        if (!player.hasEffect(ModEffects.SAFE_TIME))
            player.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME, 600, 0, false, false, false));
        if (!player.hasEffect(ModEffects.MOVE_BANED))
            player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 600, 0, false, false, false));
        if (!player.hasEffect(ModEffects.TURN_BANED))
            player.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 600, 0, false, false, false));
        if (!player.hasEffect(ModEffects.SKIN_MASK))
            player.addEffect(new MobEffectInstance(ModEffects.SKIN_MASK, 600, 0, false, false, false));
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

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
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

    private static boolean cancelSession(PlaybackSession session, ServerPlayer player,
            TimeRewindResult result) {
        if (!SESSIONS.remove(session.playerId, session)) {
            return false;
        }
        ActiveRewind active = session.current;
        if (player != null) {
            if (active != null && active.startGameType != null
                    && player.gameMode.getGameModeForPlayer() != active.startGameType) {
                player.setGameMode(active.startGameType);
            }
            player.setDeltaMovement(Vec3.ZERO);
            sendVisual(player, 0);
            removePlayerEffects(player, session);
        }
        if (active != null) {
            complete(active, result);
        }
        session.pending.forEach(pending -> complete(pending, result));
        session.pending.clear();
        session.current = null;
        return true;
    }

    private static final class PlaybackSession {
        private static final List<Holder<MobEffect>> PROTECTION_EFFECTS = List.of(
                MobEffects.INVISIBILITY,
                ModEffects.TIME_REWIND_MARK,
                ModEffects.TURN_BANED,
                ModEffects.MOVE_BANED,
                ModEffects.SKIN_MASK,
                ModEffects.INVINCIBLE,
                ModEffects.SAFE_TIME);

        private final UUID playerId;
        private final Queue<ActiveRewind> pending = new ConcurrentLinkedQueue<>();
        private final Set<Holder<MobEffect>> preExisting = ConcurrentHashMap.newKeySet();
        private boolean effectsTracked;
        private ActiveRewind current;

        private PlaybackSession(UUID playerId) {
            this.playerId = playerId;
        }

        /** Records which protection effects the player already had before the session. */
        private void trackPreExistingEffects(ServerPlayer player) {
            if (effectsTracked) {
                return;
            }
            effectsTracked = true;
            for (Holder<MobEffect> effect : PROTECTION_EFFECTS) {
                if (player.hasEffect(effect)) {
                    preExisting.add(effect);
                }
            }
        }
    }

    private static final class ActiveRewind {
        private final UUID playerId;
        private final TimeRewindSnapshot snapshot;
        private final int duration;
        private final Consumer<TimeRewindResult> completion;
        // Origin state, captured when the rewind actually activates (not when queued).
        private Vec3 start;
        private float startYRot;
        private float startXRot;
        private GameType startGameType;
        private int elapsed;

        private ActiveRewind(UUID playerId, TimeRewindSnapshot snapshot, int duration,
                Consumer<TimeRewindResult> completion) {
            this.playerId = playerId;
            this.snapshot = snapshot;
            this.duration = duration;
            this.completion = completion;
        }
    }
}
