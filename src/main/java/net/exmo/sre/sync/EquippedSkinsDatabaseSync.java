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

package net.exmo.sre.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 装备皮肤数据库上传（服务端）。
 * <p>
 * 同步策略：<b>只有"装备中的皮肤"数据会上传到远程数据库</b>，
 * 解锁列表、抽奖次数、金币等其他数据维持只读策略（由网站端唯一写入），
 * 游戏端不会上传。
 * <p>
 * 上传目标为 MySQL 同步表中独立的 {@code data_key = "equipped_skins"} 分区，
 * 与网站端写入的 {@code "skins"} / {@code "economy"} 分区互不干扰，
 * 网站端可以读取该分区获知玩家当前装备的皮肤（含帽子）。
 * <p>
 * 上传采用 5 秒防抖合并：短时间内多次换装只会上传最后一次状态；
 * 玩家断线与服务器关闭时若有未上传的变更会强制立即上传。
 */
public final class EquippedSkinsDatabaseSync {
    public static final String DATA_KEY = "equipped_skins";
    private static final Gson GSON = new GsonBuilder().create();
    private static final long DEBOUNCE_MS = 5_000L;
    private static final long BLOCKING_FLUSH_TIMEOUT_MS = 3_000L;

    /** 待上传的玩家及其到期时间 */
    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();

    private EquippedSkinsDatabaseSync() {
    }

    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(EquippedSkinsDatabaseSync::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> flushOnDisconnect(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(EquippedSkinsDatabaseSync::flushAllBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    /**
     * 标记某玩家的装备皮肤数据已变更，调度一次防抖上传。
     * 在皮肤组件的装备 setter 中调用（仅服务端生效）。
     */
    public static void queueUpload(Player player) {
        if (!(player instanceof ServerPlayer) || !isUploadEnabled()) {
            return;
        }
        PENDING.put(player.getUUID(), System.currentTimeMillis() + DEBOUNCE_MS);
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now < entry.getValue()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                upload(player, false);
            }
        }
    }

    private static void flushOnDisconnect(ServerPlayer player) {
        if (PENDING.remove(player.getUUID()) != null) {
            upload(player, true);
        }
    }

    private static void flushAllBlocking(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        MysqlPlayerDataStore.beginShutdownFlushMode();
        try {
            for (UUID uuid : PENDING.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                String payload = buildPayload(player);
                if (payload != null) {
                    MysqlPlayerDataStore.saveBatchForceBlocking(uuid, Map.of(DATA_KEY, payload),
                            System.currentTimeMillis(), BLOCKING_FLUSH_TIMEOUT_MS);
                }
            }
        } finally {
            PENDING.clear();
        }
    }

    /** 立即上传（force=true 时跳过版本冲突检查，用于断线/关服兜底） */
    private static void upload(ServerPlayer player, boolean force) {
        if (!isUploadEnabled()) {
            return;
        }
        String payload = buildPayload(player);
        if (payload == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (force) {
            MysqlPlayerDataStore.saveBatchForceAsync(player.getUUID(), Map.of(DATA_KEY, payload), now);
        } else {
            MysqlPlayerDataStore.saveBatchAsync(player.getUUID(), Map.of(DATA_KEY, payload), now);
        }
        SRE.LOGGER.debug("已上传玩家 {} 的装备皮肤数据到数据库", player.getName().getString());
    }

    /** 构造上传内容：仅包含装备中的皮肤映射 */
    private static String buildPayload(ServerPlayer player) {
        SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
        if (component == null) {
            return null;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("equipped", component.getEquippedSkins());
        data.put("updatedAt", System.currentTimeMillis());
        data.put("playerName", player.getName().getString());
        return GSON.toJson(data);
    }

    private static boolean isUploadEnabled() {
        return SREConfig.instance().mysqlPlayerSyncEnabled
                && SREConfig.instance().itemSkinSyncServerEnabled
                && MysqlPlayerDataStore.isAvailable();
    }
}
