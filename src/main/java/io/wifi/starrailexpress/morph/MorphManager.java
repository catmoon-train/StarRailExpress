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

package io.wifi.starrailexpress.morph;

import io.wifi.starrailexpress.event.AllowPlayerMorph;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameInitialized;
import io.wifi.starrailexpress.event.OnPlayerMorph;
import io.wifi.starrailexpress.network.MorphSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.events.ResetPlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端变形外观管理器。
 * 存储每位玩家当前的 {@link MorphAppearance}，并广播给所有客户端。
 */
public final class MorphManager {
    private static final Map<UUID, MorphAppearance> APPEARANCES = new ConcurrentHashMap<>();
    /** 到期游戏刻（{@code <=0} 表示直到手动解除）。 */
    private static final Map<UUID, Long> EXPIRE_AT = new ConcurrentHashMap<>();

    private MorphManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendFullSnapshot(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            APPEARANCES.remove(uuid);
            EXPIRE_AT.remove(uuid);
        });
        ServerTickEvents.END_SERVER_TICK.register(MorphManager::tickExpires);
        ResetPlayerEvent.EVENT.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                clear(serverPlayer, false);
            }
        });
        // 开局（角色分配前）与结束时清空变形，避免上一局的伪装带进下一局。
        OnGameInitialized.EVENT.register(level -> resetAll(level.getServer()));
        OnGameEnd.EVENT.register((level, game) -> resetAll(level.getServer()));
    }

    public static MorphAppearance get(UUID uuid) {
        if (uuid == null) {
            return MorphAppearance.NONE;
        }
        MorphAppearance appearance = APPEARANCES.get(uuid);
        return appearance == null ? MorphAppearance.NONE : appearance;
    }

    /**
     * 写入外观。返回是否实际发生了变化（含被事件取消的情况返回 false）。
     *
     * @param expireAtGameTick 到期游戏刻；{@code <=0} 表示不自动解除
     */
    public static boolean set(ServerPlayer player, MorphAppearance appearance, long expireAtGameTick) {
        if (player == null) {
            return false;
        }
        MorphAppearance next = appearance == null ? MorphAppearance.NONE : appearance;
        if (next.isPlayer() && player.getUUID().equals(next.targetPlayer())) {
            next = MorphAppearance.NONE;
        }
        MorphAppearance previous = get(player.getUUID());
        if (previous.equals(next)) {
            if (next.isNone()) {
                EXPIRE_AT.remove(player.getUUID());
            } else if (expireAtGameTick > 0) {
                EXPIRE_AT.put(player.getUUID(), expireAtGameTick);
            } else {
                EXPIRE_AT.remove(player.getUUID());
            }
            return false;
        }
        if (!AllowPlayerMorph.EVENT.invoker().allowMorph(player, next)) {
            return false;
        }
        UUID uuid = player.getUUID();
        if (next.isNone()) {
            APPEARANCES.remove(uuid);
            EXPIRE_AT.remove(uuid);
        } else {
            APPEARANCES.put(uuid, next);
            if (expireAtGameTick > 0) {
                EXPIRE_AT.put(uuid, expireAtGameTick);
            } else {
                EXPIRE_AT.remove(uuid);
            }
        }
        broadcastIncremental(player.getServer(), uuid, next);
        OnPlayerMorph.EVENT.invoker().onMorph(player, previous, next);
        return true;
    }

    public static boolean clear(ServerPlayer player, boolean fireIfUnchanged) {
        if (player == null) {
            return false;
        }
        if (!fireIfUnchanged && get(player.getUUID()).isNone()) {
            return false;
        }
        return set(player, MorphAppearance.NONE, 0);
    }

    public static void clearAll(MinecraftServer server) {
        if (APPEARANCES.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(APPEARANCES.keySet());
        for (UUID uuid : ids) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                clear(player, false);
            } else {
                APPEARANCES.remove(uuid);
                EXPIRE_AT.remove(uuid);
            }
        }
    }

    /**
     * 强制清空全部变形并广播空快照（游戏开始 / 结束时调用）。
     * <p>
     * 与 {@link #clearAll} 不同：不经 {@link AllowPlayerMorph}——生命周期清理不应被监听器否决；
     * 且无论服务端是否有记录都会广播一次全量空快照，确保客户端缓存与服务端一致。
     */
    public static void resetAll(MinecraftServer server) {
        Map<UUID, MorphAppearance> previous = new HashMap<>(APPEARANCES);
        APPEARANCES.clear();
        EXPIRE_AT.clear();
        if (server == null) {
            return;
        }
        for (Map.Entry<UUID, MorphAppearance> entry : previous.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                OnPlayerMorph.EVENT.invoker().onMorph(player, entry.getValue(), MorphAppearance.NONE);
            }
        }
        broadcast(server, MorphSyncPayload.full(Map.of()));
    }

    private static void tickExpires(MinecraftServer server) {
        if (EXPIRE_AT.isEmpty()) {
            return;
        }
        long now = io.wifi.starrailexpress.SRE.getTicksFromGameStart();
        List<UUID> expired = null;
        for (Map.Entry<UUID, Long> entry : EXPIRE_AT.entrySet()) {
            if (entry.getValue() > 0 && now >= entry.getValue()) {
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(entry.getKey());
            }
        }
        if (expired == null) {
            return;
        }
        for (UUID uuid : expired) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                clear(player, false);
            } else {
                APPEARANCES.remove(uuid);
                EXPIRE_AT.remove(uuid);
            }
        }
    }

    private static void sendFullSnapshot(ServerPlayer recipient) {
        MinecraftServer server = recipient.getServer();
        if (server == null || !ServerPlayNetworking.canSend(recipient, MorphSyncPayload.ID)) {
            return;
        }
        if (APPEARANCES.isEmpty()) {
            return;
        }
        Map<UUID, MorphAppearance> snapshot = new HashMap<>(APPEARANCES);
        ServerPlayNetworking.send(recipient, MorphSyncPayload.full(snapshot));
    }

    private static void broadcast(MinecraftServer server, MorphSyncPayload payload) {
        if (server == null) {
            return;
        }
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(recipient, MorphSyncPayload.ID)) {
                ServerPlayNetworking.send(recipient, payload);
            }
        }
    }

    private static void broadcastIncremental(MinecraftServer server, UUID uuid, MorphAppearance appearance) {
        broadcast(server, MorphSyncPayload.incremental(Map.of(uuid, appearance)));
    }
}
