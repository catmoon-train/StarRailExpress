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
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.fake_steve.CowardiceFakeSteveControl;
import org.agmas.noellesroles.init.ModEffects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 胆小鬼 / 暴怒 / 怯懦：附近或面前有人死亡时触发短时反应。
 */
public final class DeathReactionHandler {
    public static final double FRONT_RANGE = 24.0D;
    public static final double FRONT_COSINE = 0.4D;
    public static final double NEARBY_RANGE = 16.0D;
    public static final double RAGE_LOCK_RANGE = 12.0D;
    public static final int RAGE_SURGE_TICKS = 100;
    public static final int RAGE_SPEED_AMPLIFIER = 1;

    private static final Map<UUID, UUID> RAGE_LOCK = new ConcurrentHashMap<>();
    private static boolean registered;

    private DeathReactionHandler() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        OnPlayerDeath.EVENT.register(DeathReactionHandler::onPlayerDeath);
        ServerTickEvents.END_WORLD_TICK.register(DeathReactionHandler::tickWorld);
        OnGameEnd.EVENT.register((level, game) -> clear());
    }

    public static void clear() {
        RAGE_LOCK.clear();
        CowardiceFakeSteveControl.clear();
    }

    private static void onPlayerDeath(Player victim, net.minecraft.resources.ResourceLocation deathReason) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos deathPos = victim.blockPosition();
        for (ServerPlayer observer : level.players()) {
            if (observer.getUUID().equals(victim.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(observer)) {
                continue;
            }
            if (observer.hasEffect(ModEffects.COWARD) && isInFront(observer, victim)) {
                applyFear(observer);
            }
            boolean nearby = observer.distanceToSqr(victim) <= NEARBY_RANGE * NEARBY_RANGE;
            if (nearby && observer.hasEffect(ModEffects.RAGE)) {
                applyRageSurge(observer);
            }
            if (nearby && observer.hasEffect(ModEffects.COWARDICE)) {
                CowardiceFakeSteveControl.tryStart(observer, deathPos);
            }
        }
    }

    private static void tickWorld(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            CowardiceFakeSteveControl.tick(player);
            tickRageLook(player);
        }
    }

    private static void applyFear(ServerPlayer player) {
        if (player.hasEffect(ModEffects.FEAR)) {
            return;
        }
        player.addEffect(ModEffects.of(ModEffects.FEAR, FearEffects.DURATION_TICKS, 0, false, false, true));
        player.addEffect(ModEffects.of(ModEffects.MOVE_BANED, FearEffects.DURATION_TICKS, 0, false, false, false));
    }

    private static void applyRageSurge(ServerPlayer player) {
        player.addEffect(ModEffects.of(ModEffects.RAGE_SURGE, RAGE_SURGE_TICKS, 0, false, false, false));
        player.addEffect(ModEffects.of(ModEffects.TURN_BANED, RAGE_SURGE_TICKS, 0, false, false, false));
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed == null
                || speed.getAmplifier() < RAGE_SPEED_AMPLIFIER
                || (!speed.isInfiniteDuration() && speed.getDuration() < RAGE_SURGE_TICKS)) {
            player.addEffect(ModEffects.of(
                    MobEffects.MOVEMENT_SPEED, RAGE_SURGE_TICKS, RAGE_SPEED_AMPLIFIER, false, false, true));
        }
        ServerPlayer target = findNearest(player, null);
        if (target != null) {
            RAGE_LOCK.put(player.getUUID(), target.getUUID());
        } else {
            RAGE_LOCK.remove(player.getUUID());
        }
    }

    private static void tickRageLook(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.RAGE_SURGE) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            RAGE_LOCK.remove(player.getUUID());
            return;
        }
        UUID locked = RAGE_LOCK.get(player.getUUID());
        ServerPlayer target = findNearest(player, locked);
        if (target == null) {
            RAGE_LOCK.remove(player.getUUID());
            return;
        }
        RAGE_LOCK.put(player.getUUID(), target.getUUID());
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
    }

    private static ServerPlayer findNearest(ServerPlayer observer, UUID preferredId) {
        double maxDistanceSqr = RAGE_LOCK_RANGE * RAGE_LOCK_RANGE;
        if (preferredId != null) {
            Player preferred = observer.serverLevel().getPlayerByUUID(preferredId);
            if (preferred instanceof ServerPlayer preferredPlayer
                    && GameUtils.isPlayerAliveAndSurvival(preferredPlayer)
                    && !preferredPlayer.getUUID().equals(observer.getUUID())
                    && observer.distanceToSqr(preferredPlayer) <= maxDistanceSqr) {
                return preferredPlayer;
            }
        }
        ServerPlayer best = null;
        double bestDistance = maxDistanceSqr;
        for (ServerPlayer other : observer.serverLevel().players()) {
            if (other.getUUID().equals(observer.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            double distance = observer.distanceToSqr(other);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private static boolean isInFront(ServerPlayer observer, Player victim) {
        if (observer.distanceToSqr(victim) > FRONT_RANGE * FRONT_RANGE) {
            return false;
        }
        Vec3 direction = victim.getEyePosition().subtract(observer.getEyePosition());
        if (direction.lengthSqr() < 1.0E-4D) {
            return true;
        }
        return observer.getLookAngle().normalize().dot(direction.normalize()) >= FRONT_COSINE;
    }
}
