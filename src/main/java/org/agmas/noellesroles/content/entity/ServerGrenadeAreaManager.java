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

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.init.ModEffects;

/**
 * 手雷落点地面区域管理器（燃烧弹 / 粘液弹通用）。
 * <ul>
 * <li>{@link Type#FIRE} 燃烧弹：范围内玩家持续站立满
 * {@link #FIRE_KILL_TICKS}（2s）后死亡（离开即重置计时）。</li>
 * <li>{@link Type#SLIME} 粘液弹：范围内玩家持续获得缓慢III + 无法跳跃（跳跃提升 128 级，负跳跃力）。</li>
 * </ul>
 * 纯服务端；粒子由服务端 {@code sendParticles} 广播，无需客户端管理器。
 * 每 tick 由 {@code NRGameStateEvents} 调用 {@link #tick()}。
 */
public class ServerGrenadeAreaManager {

    public enum Type {
        FIRE, NIAOSHOU_FIRE, SLIME
    }

    /** 燃烧弹：持续站立多少 tick 后死亡（2 秒）。 */
    private static final int FIRE_KILL_TICKS = 40;
    /** 鸟兽兽燃烧弹：比普通燃烧弹快 40%（1.2 秒）。 */
    private static final int NIAOSHOU_FIRE_KILL_TICKS = 24;

    private static final List<Area> activeAreas = new ArrayList<>();
    private static final List<PendingFireKill> pendingFireKills = new ArrayList<>();

    /** 在命中点记录范围内玩家，延迟点燃击杀；用于巡飞弹的瞬时范围伤害。 */
    public static void scheduleFireKill(ServerLevel world, Vec3 position, double radius, int delayTicks,
            UUID owner) {
        AABB box = new AABB(position.x - radius, position.y - 1, position.z - radius,
                position.x + radius, position.y + 3, position.z + radius);
        List<UUID> targets = new ArrayList<>();
        for (ServerPlayer player : world.getEntitiesOfClass(ServerPlayer.class, box,
                GameUtils::isPlayerAliveAndSurvival)) {
            double dx = player.getX() - position.x;
            double dz = player.getZ() - position.z;
            if (dx * dx + dz * dz <= radius * radius) {
                player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), delayTicks));
                targets.add(player.getUUID());
            }
        }
        if (!targets.isEmpty()) {
            pendingFireKills.add(new PendingFireKill(world, targets, Math.max(1, delayTicks), owner));
        }
    }

    /** 创建一个地面区域。owner 可为 null（投掷者已下线）。 */
    public static void createArea(ServerLevel world, Vec3 position, double radius, int durationTicks,
            Type type, UUID owner) {
        activeAreas.add(new Area(world, position, radius, durationTicks, type, owner));
    }

    /** 每服务端 tick 更新所有区域。 */
    public static void tick() {
        Iterator<PendingFireKill> pendingIterator = pendingFireKills.iterator();
        while (pendingIterator.hasNext()) {
            if (pendingIterator.next().tick()) {
                pendingIterator.remove();
            }
        }
        Iterator<Area> iterator = activeAreas.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    /** 清除所有区域（如需在游戏结束时调用）。 */
    public static void clearAll() {
        activeAreas.clear();
        pendingFireKills.clear();
    }

    private static class PendingFireKill {
        private final ServerLevel world;
        private final List<UUID> targets;
        private final UUID owner;
        private int remainingTicks;

        PendingFireKill(ServerLevel world, List<UUID> targets, int remainingTicks, UUID owner) {
            this.world = world;
            this.targets = targets;
            this.remainingTicks = remainingTicks;
            this.owner = owner;
        }

        boolean tick() {
            if (--remainingTicks > 0) {
                return false;
            }
            ServerPlayer killer = owner == null ? null : world.getServer().getPlayerList().getPlayer(owner);
            for (UUID targetId : targets) {
                ServerPlayer target = world.getServer().getPlayerList().getPlayer(targetId);
                if (target != null && target.level() == world && GameUtils.isPlayerAliveAndSurvival(target)) {
                    GameUtils.killPlayer(target, true, killer, GameConstants.DeathReasons.FLAMETHROWER_BURNED);
                }
            }
            return true;
        }
    }

    private static class Area {
        private final ServerLevel world;
        private final Vec3 center;
        private final double radius;
        private final Type type;
        private final UUID owner;
        private int remainingTicks;
        private int tickCounter = 0;
        /** 燃烧弹：每名玩家已连续站立的 tick 数。 */
        private final Map<UUID, Integer> standTicks = new HashMap<>();

        Area(ServerLevel world, Vec3 center, double radius, int durationTicks, Type type, UUID owner) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.remainingTicks = durationTicks;
            this.type = type;
            this.owner = owner;
        }

        /** @return true 表示区域已过期，应移除。 */
        boolean tick() {
            remainingTicks--;
            tickCounter++;
            if (remainingTicks <= 0) {
                return true;
            }
            if (tickCounter % 2 == 0) {
                spawnParticles();
            }
            List<ServerPlayer> inside = playersInside();
            if (type == Type.SLIME) {
                applySlime(inside);
            } else {
                applyFire(inside);
            }
            return false;
        }

        private List<ServerPlayer> playersInside() {
            AABB box = new AABB(center.x - radius, center.y - 1, center.z - radius,
                    center.x + radius, center.y + 3, center.z + radius);
            List<ServerPlayer> result = new ArrayList<>();
            for (ServerPlayer player : world.getEntitiesOfClass(ServerPlayer.class, box,
                    GameUtils::isPlayerAliveAndSurvival)) {
                // 球形范围精确判定（水平距离为主，"踩在区域上"）
                double dx = player.getX() - center.x;
                double dz = player.getZ() - center.z;
                if (dx * dx + dz * dz <= radius * radius) {
                    result.add(player);
                }
            }
            return result;
        }

        private void applySlime(List<ServerPlayer> inside) {
            if (tickCounter % 5 != 0) {
                return;
            }
            for (ServerPlayer player : inside) {
                // 缓慢 III（放大值 2）
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2,
                        false, false, true));
                // 无法跳跃：0.1*11 <= 0
                player.addEffect(new MobEffectInstance(ModEffects.JUMP_DECREASE, 20, 100,
                        false, false, false));
            }
        }

        private void applyFire(List<ServerPlayer> inside) {
            Set<UUID> insideIds = new HashSet<>();
            for (ServerPlayer player : inside) {
                insideIds.add(player.getUUID());
                // 视觉反馈：点燃
                player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 20));
                int ticks = standTicks.merge(player.getUUID(), 1, Integer::sum);
                int killTicks = type == Type.NIAOSHOU_FIRE ? NIAOSHOU_FIRE_KILL_TICKS : FIRE_KILL_TICKS;
                if (ticks >= killTicks) {
                    standTicks.remove(player.getUUID());
                    ServerPlayer killer = owner == null ? null
                            : world.getServer().getPlayerList().getPlayer(owner);
                    GameUtils.killPlayer(player, true, killer, GameConstants.DeathReasons.FLAMETHROWER_BURNED);
                }
            }
            // 离开区域的玩家重置计时（不再连续）
            standTicks.keySet().retainAll(insideIds);
        }

        private void spawnParticles() {
            if (type != Type.SLIME) {
                for (int i = 0; i < 6; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * radius * 2;
                    double oz = (world.random.nextDouble() - 0.5) * radius * 2;
                    double oy = world.random.nextDouble() * 0.5;
                    world.sendParticles(ParticleTypes.FLAME, center.x + ox, center.y + oy, center.z + oz,
                            1, 0.05, 0.1, 0.05, 0.02);
                    if (i % 2 == 0) {
                        world.sendParticles(ParticleTypes.LAVA, center.x + ox, center.y + oy, center.z + oz,
                                1, 0.05, 0.05, 0.05, 0.0);
                    }
                }
            } else {
                for (int i = 0; i < 6; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * radius * 2;
                    double oz = (world.random.nextDouble() - 0.5) * radius * 2;
                    world.sendParticles(ParticleTypes.ITEM_SLIME, center.x + ox, center.y + 0.1, center.z + oz,
                            1, 0.05, 0.02, 0.05, 0.01);
                }
            }
        }
    }
}
