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

package io.wifi.starrailexpress.disguise;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.AllowPlayerDisguise;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameInitialized;
import io.wifi.starrailexpress.event.OnPlayerDisguise;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.EntityDisguiseSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 服务端实体伪装管理器。
 * <p>
 * 每位玩家最多一份伪装状态（{@link EntityDisguiseState}），外加两个可选的结束条件：
 * <ul>
 * <li>{@code EXPIRE_AT} —— 到期游戏刻；{@code <=0} 表示长期，直到手动解除。</li>
 * <li>{@code END_PREDICATES} —— 每 tick 求值，{@code test} 返回 {@code true} 即解除。</li>
 * </ul>
 * 两张表都只在真的有玩家带结束条件时才被遍历，平时 tick 只是一次 {@code isEmpty()} 后直接返回。
 * <p>
 * <b>发包策略</b>：变更先攒进 {@code PENDING}，在 tick 结束时合并成一个增量包广播
 * （见 {@link #flush}），同一玩家同一 tick 内的多次变更只发最终状态；包体本身带状态调色板，
 * 相同的实体 + NBT 只写一次。开新局 / 结束时只发一个全量空快照。玩家进服时补一次全量快照，
 * 服务端没有任何伪装时一个包都不发。
 */
public final class EntityDisguiseManager {

    private static final Map<UUID, EntityDisguiseState> STATES = new ConcurrentHashMap<>();
    /** 到期游戏刻（{@code <=0} 表示直到手动解除）。 */
    private static final Map<UUID, Long> EXPIRE_AT = new ConcurrentHashMap<>();
    /** 自定义结束条件：{@code test} 返回 {@code true} 即解除伪装。 */
    private static final Map<UUID, Predicate<ServerPlayer>> END_PREDICATES = new ConcurrentHashMap<>();
    /**
     * 本 tick 待广播的变更（uuid → 最新状态）。
     * <p>
     * 同一 tick 内的多次变更合并成一个包发出：16 人同时伪装是 1 个包 × 16 个收件人，
     * 而不是 16 个包 × 16 个收件人；同一玩家同一 tick 内改来改去也只会同步最终状态。
     */
    private static final Map<UUID, EntityDisguiseState> PENDING = new ConcurrentHashMap<>();

    private EntityDisguiseManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendFullSnapshot(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            boolean wasDisguised = !get(uuid).isNone();
            forget(uuid);
            if (wasDisguised) {
                // 让其他客户端也把这位玩家从伪装缓存里摘掉：否则他重新进服时，
                // 别人的本地缓存还留着旧伪装，会继续按旧实体渲染他。
                PENDING.put(uuid, EntityDisguiseState.NONE);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(EntityDisguiseManager::tick);
        ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                clear(serverPlayer, false);
            }
        });
        // 开局（角色分配前）与结束时清空，避免上一局的伪装带进下一局。
        OnGameInitialized.EVENT.register(level -> resetAll(level.getServer()));
        OnGameEnd.EVENT.register((level, game) -> resetAll(level.getServer()));
    }

    public static EntityDisguiseState get(@Nullable UUID uuid) {
        if (uuid == null) {
            return EntityDisguiseState.NONE;
        }
        EntityDisguiseState state = STATES.get(uuid);
        return state == null ? EntityDisguiseState.NONE : state;
    }

    /** 当前是否有任何玩家处于伪装状态（供客户端/渲染分支做零成本快速跳过）。 */
    public static boolean isEmpty() {
        return STATES.isEmpty();
    }

    /**
     * 剩余游戏刻，只读（供 {@code /sre:disguise query} 显示，不涉及包体）。
     *
     * @return {@code -1} 表示没有到期时间（无限期或由 predicate 结束）；{@code 0} 表示未伪装或已到期
     */
    public static int remainingTicks(@Nullable UUID uuid) {
        if (uuid == null || get(uuid).isNone()) {
            return 0;
        }
        long expireAt = EXPIRE_AT.getOrDefault(uuid, 0L);
        if (expireAt <= 0) {
            return -1;
        }
        return (int) Math.max(0L, expireAt - SRE.getTicksFromGameStart());
    }

    /** 是否挂了自定义结束条件（predicate）。 */
    public static boolean hasEndPredicate(@Nullable UUID uuid) {
        return uuid != null && END_PREDICATES.containsKey(uuid);
    }

    /**
     * 写入伪装状态。返回是否实际发生了变化（含被 {@link AllowPlayerDisguise} 取消的情况返回 {@code false}）。
     *
     * @param expireAtGameTick 到期游戏刻；{@code <=0} 表示不自动解除
     * @param endPredicate     自定义结束条件（可为 null）
     */
    public static boolean set(ServerPlayer player, EntityDisguiseState state, long expireAtGameTick,
            @Nullable Predicate<ServerPlayer> endPredicate) {
        if (player == null) {
            return false;
        }
        EntityDisguiseState next = state == null ? EntityDisguiseState.NONE : state;
        UUID uuid = player.getUUID();
        EntityDisguiseState previous = get(uuid);
        if (previous.equals(next)) {
            // 外观没变，但结束条件可能变了（例如「续一段时长」），单独更新。
            updateEndCondition(uuid, expireAtGameTick, endPredicate);
            return false;
        }
        if (!AllowPlayerDisguise.EVENT.invoker().allowDisguise(player, next)) {
            return false;
        }
        if (next.isNone()) {
            forget(uuid);
        } else {
            STATES.put(uuid, next);
            updateEndCondition(uuid, expireAtGameTick, endPredicate);
        }
        // 服务端立刻生效（命中判定读的是缓存下来的 eyeHeight），
        // 客户端要等本 tick 结束时的 flush —— 先同步状态，再让它 refreshDimensions。
        player.refreshDimensions();
        PENDING.put(uuid, next);
        OnPlayerDisguise.EVENT.invoker().onDisguise(player, previous, next);
        return true;
    }

    /**
     * 覆写现有伪装的外观 NBT（供 {@code /data ... sre:disguise} 使用）。
     * <p>
     * 与 {@link #set} 的区别：这里**保留原有的结束条件**（时长 / predicate），并且不经过
     * {@link AllowPlayerDisguise}——那是个「是否允许伪装 / 解除」的开关，而这里只是改一份已有伪装的
     * 外观，把它当成一次新的伪装会让语义跑偏。仍然会清洗 NBT、重算眼高、重新同步并通知
     * {@link OnPlayerDisguise}。
     *
     * @return 是否真的发生了变化
     */
    public static boolean setNbt(ServerPlayer player, @Nullable CompoundTag nbt) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUUID();
        EntityDisguiseState previous = get(uuid);
        if (previous.isNone() || previous.type() == null) {
            return false;
        }
        CompoundTag clean = EntityDisguise.sanitizeNbt(nbt);
        EntityDisguiseState next = EntityDisguiseState.of(previous.type(), clean,
                EntityDisguise.computeEyeHeight(player.level(), previous.type(), clean));
        if (previous.equals(next)) {
            return false;
        }
        STATES.put(uuid, next);
        // 服务端立刻生效，客户端等本 tick 的 flush（同 set）。
        player.refreshDimensions();
        PENDING.put(uuid, next);
        OnPlayerDisguise.EVENT.invoker().onDisguise(player, previous, next);
        return true;
    }

    public static boolean clear(ServerPlayer player, boolean fireIfUnchanged) {        if (player == null) {
            return false;
        }
        if (!fireIfUnchanged && get(player.getUUID()).isNone()) {
            return false;
        }
        return set(player, EntityDisguiseState.NONE, 0, null);
    }

    /**
     * 强制清空全部伪装并广播空快照（游戏开始 / 结束时调用）。
     * <p>
     * 与逐个 {@link #clear} 不同：不经 {@link AllowPlayerDisguise}——生命周期清理不应被监听器否决；
     * 且无论服务端是否有记录都会广播一次全量空快照，确保客户端缓存与服务端一致。
     */
    public static void resetAll(@Nullable MinecraftServer server) {
        Map<UUID, EntityDisguiseState> previous = new HashMap<>(STATES);
        STATES.clear();
        EXPIRE_AT.clear();
        END_PREDICATES.clear();
        // 全量空快照会覆盖掉还没发出去的增量，直接丢弃，避免多发包/乱序。
        PENDING.clear();
        if (server == null) {
            return;
        }
        for (Map.Entry<UUID, EntityDisguiseState> entry : previous.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            // 眼高回退同样要刷新尺寸，否则客户端相机停在上一个实体的眼高上。
            GameUtils.refreshPlayerDimension(player);
            OnPlayerDisguise.EVENT.invoker().onDisguise(player, entry.getValue(), EntityDisguiseState.NONE);
        }
        broadcast(server, EntityDisguiseSyncPayload.full(Map.of()));
    }

    private static void updateEndCondition(UUID uuid, long expireAtGameTick,
            @Nullable Predicate<ServerPlayer> endPredicate) {
        if (expireAtGameTick > 0) {
            EXPIRE_AT.put(uuid, expireAtGameTick);
        } else {
            EXPIRE_AT.remove(uuid);
        }
        if (endPredicate != null) {
            END_PREDICATES.put(uuid, endPredicate);
        } else {
            END_PREDICATES.remove(uuid);
        }
    }

    private static void forget(UUID uuid) {
        STATES.remove(uuid);
        EXPIRE_AT.remove(uuid);
        END_PREDICATES.remove(uuid);
        PENDING.remove(uuid);
    }

    private static void tick(MinecraftServer server) {
        if (!EXPIRE_AT.isEmpty() || !END_PREDICATES.isEmpty()) {
            expire(server);
        }
        // 无论本 tick 有没有到期，都要把攒下的变更发出去（正常情况下这里是空的）。
        flush(server);
    }

    /** 处理到期与自定义结束条件。 */
    private static void expire(MinecraftServer server) {
        long now = SRE.getTicksFromGameStart();
        List<UUID> finished = null;
        for (Map.Entry<UUID, Long> entry : EXPIRE_AT.entrySet()) {
            if (entry.getValue() > 0 && now >= entry.getValue()) {
                finished = addFinished(finished, entry.getKey());
            }
        }
        for (Map.Entry<UUID, Predicate<ServerPlayer>> entry : END_PREDICATES.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // 玩家已离线（DISCONNECT 本应清掉），这里兜底，避免条件表无限增长。
                finished = addFinished(finished, entry.getKey());
                continue;
            }
            boolean done;
            try {
                done = entry.getValue().test(player);
            } catch (Throwable throwable) {
                // 条件抛异常时按「已结束」处理，否则这条伪装会永久卡住。
                SRE.LOGGER.warn("EntityDisguise 结束条件抛出异常，已强制解除 {}", player.getGameProfile().getName(),
                        throwable);
                done = true;
            }
            if (done) {
                finished = addFinished(finished, entry.getKey());
            }
        }
        if (finished == null) {
            return;
        }
        for (UUID uuid : finished) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                clear(player, false);
            } else {
                forget(uuid);
            }
        }
    }

    /**
     * 把本 tick 攒下的变更合并成一个增量包发出，随后只给受影响玩家补一个
     * {@code RefreshDimensionsS2CPacket}。
     * <p>
     * 顺序很重要：同一条连接上先到状态包、后到刷新包，客户端才是「先知道伪装成什么，
     * 再按新眼高 refreshDimensions」——反过来的话眼高不会生效。
     */
    private static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Map<UUID, EntityDisguiseState> batch = new HashMap<>(PENDING);
        PENDING.clear();
        broadcast(server, EntityDisguiseSyncPayload.incremental(batch));
        for (Map.Entry<UUID, EntityDisguiseState> entry : batch.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                GameUtils.refreshPlayerDimension(player);
            }
        }
    }

    private static List<UUID> addFinished(@Nullable List<UUID> finished, UUID uuid) {
        if (finished == null) {
            finished = new ArrayList<>();
        }
        finished.add(uuid);
        return finished;
    }

    private static void sendFullSnapshot(ServerPlayer recipient) {
        MinecraftServer server = recipient.getServer();
        if (server == null || STATES.isEmpty() || !ServerPlayNetworking.canSend(recipient, EntityDisguiseSyncPayload.ID)) {
            return;
        }
        ServerPlayNetworking.send(recipient,
                EntityDisguiseSyncPayload.full(new HashMap<>(STATES)));
    }

    private static void broadcast(@Nullable MinecraftServer server, EntityDisguiseSyncPayload payload) {
        if (server == null) {
            return;
        }
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(recipient, EntityDisguiseSyncPayload.ID)) {
                ServerPlayNetworking.send(recipient, payload);
            }
        }
    }
}
