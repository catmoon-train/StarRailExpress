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

package net.exmo.sre.record;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import net.exmo.sre.record.network.RecordListS2CPayload;
import net.exmo.sre.record.network.RecordReplayS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局战绩服务端逻辑：在对局结束时把回放时间线落库，并响应客户端的列表 / 回放查询请求。
 */
public final class MatchRecordService {

    private static final Logger logger = LoggerFactory.getLogger(MatchRecordService.class);
    private static final int DEFAULT_LIST_LIMIT = 50;

    private MatchRecordService() {
    }

    /**
     * 对局结束时调用：把当前回放管理器中的完整时间线构造成 {@link MatchRecord} 并异步保存到远端数据库。
     * 若远端数据库不可用（未开启 MySQL 同步）则静默跳过。
     */
    public static void recordFinishedMatch(ServerLevel level) {
        if (level == null || !MatchRecordStore.isAvailable() || !SREConfig.instance().isStatsSyncEnabled) {
            return;
        }
        GameReplayManager manager = SRE.REPLAY_MANAGER;
        if (manager == null || manager.currentReplayData == null) {
            return;
        }
        try {
            MatchRecord record = build(manager, manager.currentReplayData, level.registryAccess());
            MatchRecordStore.saveAsync(record).whenComplete((ok, err) -> {
                if (err != null) {
                    logger.warn("异步保存全局战绩失败。", err);
                } else if (Boolean.TRUE.equals(ok)) {
                    logger.info("全局战绩 {} 已保存（{} 条事件）。", record.matchId, record.events.size());
                }
            });
        } catch (Exception exception) {
            logger.warn("构造全局战绩时出错，已跳过本局保存。", exception);
        }
    }

    private static MatchRecord build(GameReplayManager manager, GameReplayData data, RegistryAccess registryAccess) {
        MatchRecord record = new MatchRecord();
        record.matchId = UUID.randomUUID().toString();
        record.createdAt = System.currentTimeMillis();
        record.winningTeam = data.getWinningTeam();
        MutableComponent title = data.getWinningTitle();
        record.winningTitleJson = title == null ? null : Component.Serializer.toJson(title, registryAccess);
        record.playerCount = data.getPlayerCount();

        // 扫描换职业事件，得到每位玩家的「最初旧职业」与「最终现职业」
        Map<UUID, String> firstOldRole = new HashMap<>();
        Map<UUID, String> lastNewRole = new HashMap<>();
        for (GameReplayData.ReplayEvent event : data.getTimeline()) {
            if (event.getType() != GameReplayData.EventType.CHANGE_ROLE) {
                continue;
            }
            UUID player = event.getSourcePlayer();
            String message = event.getMessage();
            if (player == null || message == null) {
                continue;
            }
            int separator = message.indexOf("===");
            if (separator < 0) {
                continue;
            }
            firstOldRole.putIfAbsent(player, message.substring(0, separator));
            lastNewRole.put(player, message.substring(separator + 3));
        }

        Map<UUID, String> roles = data.getPlayerRoles();
        if (roles != null) {
            for (Map.Entry<UUID, String> entry : roles.entrySet()) {
                UUID uuid = entry.getKey();
                if (uuid == null) {
                    continue;
                }
                String currentRole = lastNewRole.getOrDefault(uuid, entry.getValue());
                String oldRole = firstOldRole.get(uuid);
                if (oldRole != null && oldRole.equals(currentRole)) {
                    oldRole = null;
                }
                MatchRecord.MatchPlayer player = new MatchRecord.MatchPlayer();
                player.uuid = uuid.toString();
                player.name = GameReplayManager.playerNames.getOrDefault(uuid, uuid.toString());
                player.roleId = currentRole;
                player.oldRoleId = oldRole;
                player.faction = factionOf(currentRole);
                record.players.add(player);
            }
        }

        for (ReplayTimelineEvent event : manager.getTimelineEvents(true)) {
            MatchRecord.MatchEvent matchEvent = new MatchRecord.MatchEvent();
            matchEvent.type = event.type() == null ? "CUSTOM_EVENT" : event.type().name();
            matchEvent.relativeTimestamp = event.relativeTimestamp();
            matchEvent.actorName = event.actor() == null ? null : event.actor().name();
            matchEvent.textJson = event.text() == null
                    ? null
                    : Component.Serializer.toJson(event.text(), registryAccess);
            matchEvent.hidden = event.hidden();
            record.events.add(matchEvent);
        }
        return record;
    }

    private static int factionOf(String roleId) {
        if (roleId == null) {
            return -1;
        }
        ResourceLocation id = ResourceLocation.tryParse(roleId);
        if (id == null) {
            return -1;
        }
        SRERole role = TMMRoles.ROLES.get(id);
        return role == null ? -1 : PlayerRoleWeightManager.getRoleType(role);
    }

    /** 响应「按需拉取战绩列表的一页」请求：仅查询并同步该窗口，减少流量与数据库负载。 */
    public static void openListWindow(ServerPlayer player, int offset, int limit) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        int requested = limit <= 0 ? DEFAULT_LIST_LIMIT : limit;
        int safeOffset = Math.max(0, offset);
        MatchRecordStore.listWindowAsync(safeOffset, requested).whenComplete((page, err) -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (!isOnline(server, player)) {
                    return;
                }
                if (err != null || page == null) {
                    ServerPlayNetworking.send(player, new RecordListS2CPayload(safeOffset, 0, "[]"));
                    return;
                }
                ServerPlayNetworking.send(player, new RecordListS2CPayload(page.offset(), page.total(),
                        MatchRecord.summaryListToJson(page.items())));
            });
        });
    }

    /** 响应「打开某场回放」请求：异步读取完整记录并同步给玩家。 */
    public static void openReplayFor(ServerPlayer player, String matchId) {
        if (player == null || matchId == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        MatchRecordStore.loadAsync(matchId).whenComplete((record, err) -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (!isOnline(server, player)) {
                    return;
                }
                String json = (err != null || record == null || record.isEmpty())
                        ? ""
                        : record.get().toJson();
                ServerPlayNetworking.send(player, new RecordReplayS2CPayload(matchId, json));
            });
        });
    }

    private static boolean isOnline(MinecraftServer server, ServerPlayer player) {
        return server.getPlayerList().getPlayer(player.getUUID()) != null;
    }
}
