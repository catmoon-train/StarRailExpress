/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.fake_steve.AphreniaFakeSteveControl;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server runtime for appetite loss, missing lungs, aphrenia, intellect drop, and ataxia.
 */
public final class StatusAilmentHandler {
    private static final Map<UUID, Long> LUNG_DEATH_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Episode> APHRENIA = new ConcurrentHashMap<>();
    private static final Map<UUID, Episode> INTELLECT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MOTOR_COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private static boolean registered;

    private StatusAilmentHandler() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!player.hasEffect(ModEffects.LOSS_OF_APPETITE) || !StatusAilmentItems.isFoodOrDrink(stack)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.noellesroles.ailment.appetite_banned")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        });
        ServerTickEvents.END_WORLD_TICK.register(StatusAilmentHandler::tickWorld);
        OnGameEnd.EVENT.register((level, game) -> clear());
    }

    public static boolean isIntellectLocked(Player player) {
        if (player == null) {
            return false;
        }
        Episode episode = INTELLECT.get(player.getUUID());
        return episode != null && episode.active;
    }

    public static void onMissingLungsEnded(ServerPlayer player) {
        if (player != null) {
            LUNG_DEATH_AT.remove(player.getUUID());
        }
    }

    public static void onAphreniaEnded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        APHRENIA.remove(player.getUUID());
        AphreniaFakeSteveControl.stop(player);
    }

    public static void onIntellectEnded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Episode episode = INTELLECT.remove(player.getUUID());
        if (episode != null && episode.active) {
            endIntellectLock(player);
        }
    }

    public static void clear() {
        LUNG_DEATH_AT.clear();
        APHRENIA.clear();
        INTELLECT.clear();
        MOTOR_COOLDOWN_UNTIL.clear();
        AphreniaFakeSteveControl.clear();
    }

    private static void tickWorld(ServerLevel level) {
        long now = now(level);
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                cleanupDead(player);
                continue;
            }
            tickMissingLungs(player, now);
            tickAphrenia(player, now);
            tickIntellect(player, now);
            tickMotor(player, now);
        }
    }

    private static void tickMissingLungs(ServerPlayer player, long now) {
        MobEffectInstance instance = player.getEffect(ModEffects.MISSING_LUNGS);
        if (instance == null) {
            LUNG_DEATH_AT.remove(player.getUUID());
            return;
        }
        long deathAt = LUNG_DEATH_AT.computeIfAbsent(player.getUUID(),
                id -> now + StatusAilmentPolicy.missingLungsTicks(instance.getAmplifier()));
        if (now >= deathAt) {
            LUNG_DEATH_AT.remove(player.getUUID());
            player.removeEffect(ModEffects.MISSING_LUNGS);
            GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.MISSING_LUNGS);
        }
    }

    private static void tickAphrenia(ServerPlayer player, long now) {
        MobEffectInstance instance = player.getEffect(ModEffects.APHRENIA);
        if (instance == null) {
            if (APHRENIA.remove(player.getUUID()) != null) {
                AphreniaFakeSteveControl.stop(player);
            }
            return;
        }
        int amplifier = instance.getAmplifier();
        Episode episode = APHRENIA.get(player.getUUID());
        if (episode == null) {
            APHRENIA.put(player.getUUID(), scheduleEpisode(player, now, amplifier, false));
            return;
        }
        if (episode.active) {
            if (now >= episode.endAt) {
                AphreniaFakeSteveControl.stop(player);
                APHRENIA.put(player.getUUID(), scheduleEpisode(player, now, amplifier, false));
            } else {
                AphreniaFakeSteveControl.tick(player);
            }
            return;
        }
        if (now >= episode.nextAt) {
            Episode started = scheduleEpisode(player, now, amplifier, true);
            APHRENIA.put(player.getUUID(), started);
            AphreniaFakeSteveControl.start(player);
            AphreniaFakeSteveControl.tick(player);
        }
    }

    private static void tickIntellect(ServerPlayer player, long now) {
        MobEffectInstance instance = player.getEffect(ModEffects.INTELLECT_DROP);
        if (instance == null) {
            Episode removed = INTELLECT.remove(player.getUUID());
            if (removed != null && removed.active) {
                endIntellectLock(player);
            }
            return;
        }
        int amplifier = instance.getAmplifier();
        Episode episode = INTELLECT.get(player.getUUID());
        if (episode == null) {
            INTELLECT.put(player.getUUID(), scheduleIntellect(player, now, amplifier, false));
            return;
        }
        if (episode.active) {
            if (now >= episode.endAt) {
                endIntellectLock(player);
                INTELLECT.put(player.getUUID(), scheduleIntellect(player, now, amplifier, false));
            }
            return;
        }
        if (now >= episode.nextAt) {
            Episode started = scheduleIntellect(player, now, amplifier, true);
            INTELLECT.put(player.getUUID(), started);
            player.addEffect(new MobEffectInstance(ModEffects.USED_BANED,
                    (int) Math.max(1L, started.endAt - now), 0, false, false, false));
        }
    }

    private static void tickMotor(ServerPlayer player, long now) {
        MobEffectInstance instance = player.getEffect(ModEffects.MOTOR_DYSFUNCTION);
        if (instance == null) {
            MOTOR_COOLDOWN_UNTIL.remove(player.getUUID());
            return;
        }
        if (player.hasEffect(ModEffects.SWIM_POSE)) {
            return;
        }
        long cooldownUntil = MOTOR_COOLDOWN_UNTIL.getOrDefault(player.getUUID(), 0L);
        if (now < cooldownUntil) {
            return;
        }
        if (player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) {
            return;
        }
        int amplifier = instance.getAmplifier();
        if (player.getRandom().nextFloat() >= StatusAilmentPolicy.motorFallChance(amplifier)) {
            return;
        }
        int fallTicks = StatusAilmentPolicy.motorFallDurationTicks(amplifier);
        player.addEffect(new MobEffectInstance(ModEffects.SWIM_POSE, fallTicks, 0, false, false, true));
        MOTOR_COOLDOWN_UNTIL.put(player.getUUID(),
                now + fallTicks + StatusAilmentPolicy.motorFallCooldownTicks(amplifier));
    }

    private static Episode scheduleEpisode(ServerPlayer player, long now, int amplifier, boolean startNow) {
        if (startNow) {
            int duration = StatusAilmentPolicy.pickRange(
                    StatusAilmentPolicy.aphreniaMinDurationTicks(amplifier),
                    StatusAilmentPolicy.aphreniaMaxDurationTicks(amplifier),
                    player.getRandom().nextInt());
            return new Episode(now, now + duration, true);
        }
        int interval = StatusAilmentPolicy.pickRange(
                StatusAilmentPolicy.aphreniaMinIntervalTicks(amplifier),
                StatusAilmentPolicy.aphreniaMaxIntervalTicks(amplifier),
                player.getRandom().nextInt());
        return new Episode(now + interval, 0L, false);
    }

    private static Episode scheduleIntellect(ServerPlayer player, long now, int amplifier, boolean startNow) {
        if (startNow) {
            int duration = StatusAilmentPolicy.pickRange(
                    StatusAilmentPolicy.intellectMinDurationTicks(amplifier),
                    StatusAilmentPolicy.intellectMaxDurationTicks(amplifier),
                    player.getRandom().nextInt());
            return new Episode(now, now + duration, true);
        }
        int interval = StatusAilmentPolicy.pickRange(
                StatusAilmentPolicy.intellectMinIntervalTicks(amplifier),
                StatusAilmentPolicy.intellectMaxIntervalTicks(amplifier),
                player.getRandom().nextInt());
        return new Episode(now + interval, 0L, false);
    }

    private static void endIntellectLock(ServerPlayer player) {
        MobEffectInstance used = player.getEffect(ModEffects.USED_BANED);
        if (used != null && used.getDuration() > 0
                && used.getDuration() <= StatusAilmentPolicy.intellectMaxDurationTicks(8)) {
            player.removeEffect(ModEffects.USED_BANED);
        }
    }

    private static void cleanupDead(ServerPlayer player) {
        UUID id = player.getUUID();
        LUNG_DEATH_AT.remove(id);
        MOTOR_COOLDOWN_UNTIL.remove(id);
        if (APHRENIA.remove(id) != null) {
            AphreniaFakeSteveControl.stop(player);
        }
        Episode intellect = INTELLECT.remove(id);
        if (intellect != null && intellect.active) {
            endIntellectLock(player);
        }
    }

    private static long now(ServerLevel level) {
        long ticks = GameUtils.getTicksFromGameStart(level);
        return ticks > 0L ? ticks : level.getGameTime();
    }

    private record Episode(long nextAt, long endAt, boolean active) {
    }
}
