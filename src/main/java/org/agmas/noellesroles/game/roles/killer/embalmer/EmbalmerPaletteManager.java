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

package org.agmas.noellesroles.game.roles.killer.embalmer;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.packet.EmbalmerPaletteS2CPacket;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 嬉命人·调色盘（技能二）。
 *
 * <p>以自身为圆心、8 格半径的球形范围内，给所有存活玩家实体与玩家尸体实体各染一层
 * 随机 RGB（每个目标颜色互不相同）。仅影响渲染颜色，不影响任何玩法数据。
 *
 * <p>服务端权威，通过 {@link EmbalmerPaletteS2CPacket} 下发给客户端渲染层消费。
 */
public final class EmbalmerPaletteManager {

    /** 球形半径（格）。 */
    public static final double RADIUS = 8.0D;
    /** 染色持续时间（tick）：3 分钟。 */
    public static final int DURATION_TICKS = 180 * 20;
    /** 技能冷却（tick）。 */
    public static final int COOLDOWN_TICKS = 60 * 20;

    /** {@code player} 为 true 表示染色目标是玩家实体；false 表示玩家尸体。 */
    private record Tint(int color, long until, boolean player) {
    }

    private static final Map<UUID, Tint> TINTS = new ConcurrentHashMap<>();
    private static int syncTick = 0;

    private EmbalmerPaletteManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(EmbalmerPaletteManager::tick);
        // 游戏结束时清空并立即通知所有客户端撤掉染色
        OnGameEnd.EVENT.register((level, game) -> {
            clear();
            if (level != null) {
                broadcast(level);
            }
        });
    }

    /**
     * 发动调色盘。
     *
     * @return 是否成功（范围内没有任何可染色目标时返回 false）
     */
    public static boolean activate(ServerPlayer caster) {
        if (caster == null) {
            return false;
        }
        ServerLevel level = caster.serverLevel();
        AABB box = caster.getBoundingBox().inflate(RADIUS);
        List<UUID> targets = new ArrayList<>();
        List<UUID> bodies = new ArrayList<>();

        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (target.distanceTo(caster) > RADIUS) {
                continue;
            }
            targets.add(target.getUUID());
        }

        for (PlayerBodyEntity body : level.getEntitiesOfClass(PlayerBodyEntity.class, box)) {
            if (body.distanceTo(caster) > RADIUS) {
                continue;
            }
            bodies.add(body.getUUID());
        }

        if (targets.isEmpty() && bodies.isEmpty()) {
            caster.displayClientMessage(
                    Component.translatable("message.noellesroles.embalmer.palette.no_target"), true);
            return false;
        }

        long until = level.getGameTime() + DURATION_TICKS;
        for (UUID id : targets) {
            TINTS.put(id, new Tint(randomColor(), until, true));
        }
        for (UUID id : bodies) {
            TINTS.put(id, new Tint(randomColor(), until, false));
        }
        broadcast(level);
        return true;
    }

    /** 随机但饱和度/亮度稳定的颜色，保证可见性。 */
    private static int randomColor() {
        return Color.HSBtoRGB(ThreadLocalRandom.current().nextFloat(), 0.94F, 0.82F) & 0xFFFFFF;
    }

    public static void clear() {
        TINTS.clear();
    }

    private static void broadcast(ServerLevel level) {
        EmbalmerPaletteS2CPacket payload = payloadFor(level);
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static EmbalmerPaletteS2CPacket payloadFor(ServerLevel level) {
        long now = level.getGameTime();
        Map<UUID, Integer> active = new java.util.HashMap<>();
        int remaining = 0;
        for (Map.Entry<UUID, Tint> entry : TINTS.entrySet()) {
            if (entry.getValue().until() <= now) {
                continue;
            }
            active.put(entry.getKey(), entry.getValue().color());
            // 取最长剩余时间，避免客户端提前把仍在生效的染色清掉
            remaining = (int) Math.max(remaining, Math.min(Integer.MAX_VALUE, entry.getValue().until() - now));
        }
        return new EmbalmerPaletteS2CPacket(active, remaining);
    }

    private static void tick(ServerLevel level) {
        if (TINTS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean changed = false;
        var iterator = TINTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Tint tint = entry.getValue();
            // 持续时间到
            if (now >= tint.until()) {
                iterator.remove();
                changed = true;
                continue;
            }
            // 被染色的玩家死亡（变旁观者）/ 离线 / 换维度：立刻撤除颜色
            if (tint.player() && !isPaintedPlayerStillValid(level, entry.getKey())) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            broadcast(level);
            return;
        }
        if (++syncTick >= 40) {
            syncTick = 0;
            broadcast(level);
        }
    }

    /** 被染色的玩家仍在局内、存活且处于冒险模式时，染色才继续生效。 */
    private static boolean isPaintedPlayerStillValid(ServerLevel level, UUID uuid) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
        return player != null
                && !player.isRemoved()
                && player.level() == level
                && GameUtils.isPlayerAliveAndSurvival(player);
    }
}
