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

import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嬉命人·语音互换。
 *
 * <p>原理：不交换音频数据，只交换「声音发出的位置」。A 说话时，声音从 B 所在的位置发出
 * （语音 HUD 上显示的说话人仍是 A）。配对是双向的，因此 A、B 两人的语音发声位置互换。
 *
 * <p>服务端权威；客户端通过 {@code EmbalmerRoleData#voiceSwapCooldown} 读取冷却。
 *
 * <p>线程模型（与 {@code VillageGhostVoice} 一致）：主线程维护 {@link #LINKS}，并把结果发布成
 * 不可变快照 {@link #ROUTES}；SVC 语音线程只读这个快照，不触碰 CCA、玩家列表或实体坐标。
 * 这样语音线程不会因读到半更新状态、或抛出被 SVC 吞掉的异常，让整次转发音信全无。
 *
 * <p>结束条件：游戏结束、持续时间到时、参与互换的任意一方死亡（变成旁观者 / 离开冒险模式）、
 * 任意一方离线或换维度。
 */
public final class EmbalmerVoiceSwapManager {

    /** 单次互换持续时间（tick）：2 分钟。 */
    public static final int DURATION_TICKS = 120 * 20;
    /** 互换后的技能冷却（tick）：80 秒。 */
    public static final int COOLDOWN_TICKS = 80 * 20;

    private record Link(UUID partner, long until) {
    }

    /**
     * 语音线程可见的路由：说话者 → 声源位置 + 到期时间。
     *
     * <p>只含不可变数据，语音线程不会再触碰游戏世界：连记录听众用的维度也在这里一并发布，
     * 避免在 SVC 语音线程上调用 {@code ServerPlayer#serverLevel()}（跨线程访问服务端世界
     * 一旦被 {@code dispatchEvent} 吞掉异常，就会表现为「技能毫无反应」）。
     */
    public record Route(UUID speaker, UUID body, Vec3 origin, ServerLevel level, long until) {
    }

    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();
    /** 主线程发布、语音线程读取的不可变快照。 */
    private static volatile Map<UUID, Route> ROUTES = Map.of();

    private EmbalmerVoiceSwapManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(EmbalmerVoiceSwapManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> remove(handler.getPlayer().getUUID()));
        OnGameEnd.EVENT.register((level, game) -> clear());
    }

    /** 建立双向配对。 */
    public static void swap(ServerPlayer first, ServerPlayer second, long now) {
        if (first == null || second == null || first.getUUID().equals(second.getUUID())) {
            return;
        }
        long until = now + DURATION_TICKS;
        remove(first.getUUID());
        remove(second.getUUID());
        LINKS.put(first.getUUID(), new Link(second.getUUID(), until));
        LINKS.put(second.getUUID(), new Link(first.getUUID(), until));
        publish(first.getServer());
        Noellesroles.LOGGER.info("[Embalmer] Voice swap established: {} <-> {} for {} ticks",
                first.getName().getString(), second.getName().getString(), DURATION_TICKS);
    }

    /** 移除某人的配对（会成对移除，避免残留单向链接）。 */
    public static void remove(UUID player) {
        if (player == null) {
            return;
        }
        Link old = LINKS.remove(player);
        if (old == null) {
            return;
        }
        Link reverse = LINKS.get(old.partner());
        if (reverse != null && reverse.partner().equals(player)) {
            LINKS.remove(old.partner());
        }
        // 立即把这条路由从语音线程可见的快照里摘掉，避免解除后仍从旧位置发声
        ROUTES = removeRoute(ROUTES, player, old.partner());
    }

    /** 从快照里剔除一对路由（不改动 {@link #LINKS}）。 */
    private static Map<UUID, Route> removeRoute(Map<UUID, Route> current, UUID first, UUID second) {
        if (current.isEmpty()) {
            return current;
        }
        Map<UUID, Route> next = new HashMap<>(current);
        next.remove(first);
        next.remove(second);
        return Map.copyOf(next);
    }

    public static void clear() {
        LINKS.clear();
        ROUTES = Map.of();
    }

    /**
     * 语音线程用：返回说话者当前应当使用的声源路由，没有配对（或已过期）时返回 {@code null}。
     *
     * <p>只读 {@link #ROUTES} 快照，不读取玩家实体与 CCA，因此可以安全地在 SVC 线程调用。
     */
    @Nullable
    public static Route route(UUID speaker) {
        if (speaker == null) {
            return null;
        }
        Route route = ROUTES.get(speaker);
        if (route == null) {
            return null;
        }
        // 快照由主线程按时清理；这里再按系统时间兜底，避免过期路由继续转发
        return System.currentTimeMillis() >= route.until() ? null : route;
    }

    /**
     * 主线程用：返回说话者当前的声音宿主；没有配对（或已过期）时返回 {@code null}，
     * 此时走原版默认广播。
     */
    @Nullable
    public static ServerPlayer voiceBody(ServerPlayer speaker) {
        if (speaker == null) {
            return null;
        }
        // 说话者自己已经死亡/旁观：立即结束这一对互换
        if (!isSwapTargetValid(speaker.level(), speaker)) {
            remove(speaker.getUUID());
            return null;
        }
        Link link = LINKS.get(speaker.getUUID());
        if (link == null) {
            return null;
        }
        if (speaker.level().getGameTime() >= link.until()) {
            remove(speaker.getUUID());
            return null;
        }
        ServerPlayer partner = speaker.getServer().getPlayerList().getPlayer(link.partner());
        if (!isSwapTargetValid(speaker.level(), partner)) {
            // 对方死亡 / 离线 / 换维度：结束这一对互换
            remove(speaker.getUUID());
            return null;
        }
        return partner;
    }

    /**
     * 由主线程重建并发布路由快照。
     *
     * <p>每次配对变化、以及每个 tick 都会调用一次，保证声源位置跟随对方移动。
     *
     * @param server 主线程的服务器实例，用于解析玩家；语音线程不会调用本方法
     */
    private static void publish(MinecraftServer server) {
        if (server == null) {
            ROUTES = Map.of();
            return;
        }
        if (LINKS.isEmpty()) {
            ROUTES = Map.of();
            return;
        }
        long nowMillis = System.currentTimeMillis();
        Map<UUID, Route> next = new HashMap<>();
        for (Map.Entry<UUID, Link> entry : LINKS.entrySet()) {
            UUID speaker = entry.getKey();
            Link link = entry.getValue();
            ServerPlayer self = server.getPlayerList().getPlayer(speaker);
            ServerPlayer body = server.getPlayerList().getPlayer(link.partner());
            if (self == null || body == null
                    || !isSwapTargetValid(self.level(), self)
                    || !isSwapTargetValid(self.level(), body)) {
                continue;
            }
            // 剩余 tick 换算成墙钟毫秒，供语音线程判断过期
            long remainTicks = Math.max(0L, link.until() - self.level().getGameTime());
            long untilMillis = nowMillis + remainTicks * 50L;
            next.put(speaker, new Route(speaker, link.partner(), body.position(), self.serverLevel(), untilMillis));
        }
        ROUTES = Map.copyOf(next);
    }

    /** 参与语音互换者必须是在同一维度、且存活（冒险模式）的玩家。 */
    private static boolean isSwapTargetValid(@Nullable Level level, ServerPlayer player) {
        return player != null
                && !player.isRemoved()
                && level != null
                && player.level() == level
                && GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static void tick(ServerLevel world) {
        if (LINKS.isEmpty()) {
            ROUTES = Map.of();
            return;
        }
        // END_WORLD_TICK 对每个已加载维度各触发一次；配对双方可能不在当前 world，
        // 因此不能用 tick 传入的 world 做存活校验，否则末地/下界先 tick 时会把配对误删。
        long now = world.getGameTime();
        List<UUID> broken = new ArrayList<>();
        for (Map.Entry<UUID, Link> entry : LINKS.entrySet()) {
            UUID self = entry.getKey();
            Link link = entry.getValue();
            if (now >= link.until()) {
                broken.add(self);
                continue;
            }
            ServerPlayer selfPlayer = world.getServer().getPlayerList().getPlayer(self);
            ServerPlayer partner = world.getServer().getPlayerList().getPlayer(link.partner());
            // 以「说话者自己的维度」为基准：双方必须在同一维度且都存活
            if (selfPlayer == null || !isSwapTargetValid(selfPlayer.level(), selfPlayer)
                    || !isSwapTargetValid(selfPlayer.level(), partner)) {
                broken.add(self);
            }
        }
        for (UUID id : broken) {
            remove(id);
        }
        // 每一 tick 重新发布，声源位置才会跟随对方移动
        publish(world.getServer());
    }
}
