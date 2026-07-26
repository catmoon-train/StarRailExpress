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

/**
 * 手雷落点地面区域管理器（燃烧弹 / 粘液弹通用）。
 * <ul>
 *   <li>{@link Type#FIRE} 燃烧弹：范围内玩家持续站立满 {@link #FIRE_KILL_TICKS}（2s）后死亡（离开即重置计时）。</li>
 *   <li>{@link Type#SLIME} 粘液弹：范围内玩家持续获得缓慢III + 无法跳跃（跳跃提升 128 级，负跳跃力）。</li>
 * </ul>
 * 纯服务端；粒子由服务端 {@code sendParticles} 广播，无需客户端管理器。
 * 每 tick 由 {@code NRGameStateEvents} 调用 {@link #tick()}。
 */
public class ServerGrenadeAreaManager {

    public enum Type { FIRE, SLIME }

    /** 燃烧弹：持续站立多少 tick 后死亡（2 秒）。 */
    private static final int FIRE_KILL_TICKS = 40;

    private static final List<Area> activeAreas = new ArrayList<>();

    /** 创建一个地面区域。owner 可为 null（投掷者已下线）。 */
    public static void createArea(ServerLevel world, Vec3 position, double radius, int durationTicks,
            Type type, UUID owner) {
        activeAreas.add(new Area(world, position, radius, durationTicks, type, owner));
    }

    /** 每服务端 tick 更新所有区域。 */
    public static void tick() {
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
                // 无法跳跃：跳跃提升放大值 128（作为 byte 溢出为负，跳跃力为负 → 无法起跳）
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 128,
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
                if (ticks >= FIRE_KILL_TICKS) {
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
            if (type == Type.FIRE) {
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
