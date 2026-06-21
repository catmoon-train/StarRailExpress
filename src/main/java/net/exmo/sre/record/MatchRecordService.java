package net.exmo.sre.record;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import net.exmo.sre.record.network.RecordListS2CPayload;
import net.exmo.sre.record.network.RecordReplayS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
        if (level == null || !MatchRecordStore.isAvailable()) {
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

        Map<UUID, String> roles = data.getPlayerRoles();
        if (roles != null) {
            for (Map.Entry<UUID, String> entry : roles.entrySet()) {
                UUID uuid = entry.getKey();
                if (uuid == null) {
                    continue;
                }
                MatchRecord.MatchPlayer player = new MatchRecord.MatchPlayer();
                player.uuid = uuid.toString();
                player.name = GameReplayManager.playerNames.getOrDefault(uuid, uuid.toString());
                player.roleId = entry.getValue();
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

    /** 响应「打开战绩列表」请求：异步查询数据库并把结果同步给玩家。 */
    public static void openListFor(ServerPlayer player, int limit) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        int requested = limit <= 0 ? DEFAULT_LIST_LIMIT : limit;
        MatchRecordStore.listRecentAsync(requested).whenComplete((summaries, err) -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (!isOnline(server, player)) {
                    return;
                }
                String json = (err != null || summaries == null)
                        ? "[]"
                        : MatchRecord.summaryListToJson(summaries);
                ServerPlayNetworking.send(player, new RecordListS2CPayload(json));
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
