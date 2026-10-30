package org.agmas.noellesroles.game.halloween.online;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 服务器生命周期适配层；每个服务器启动一次，不随加入、换图、关局重建发送器。 */
public final class HalloweenOnlineSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(HalloweenOnlineSync.class);
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private static boolean registered;
    private static final class State {
        boolean enabled;
        EarningsSender sender;
        long lastDiskWarning;
    }
    private HalloweenOnlineSync() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(HalloweenOnlineSync::start);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 已发生的收益不受当前活动/地图开关限制；失败凭据在真实背包内重试。
            for (ServerPlayer player : server.getPlayerList().getPlayers()) OnlinePumpkinIncome.retryInventory(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) -> {
            OnlinePumpkinIncome.retryInventory(connection.player);
            State state = SERVERS.get(server);
            if (state != null && state.sender != null) state.sender.requestFlush();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) OnlinePumpkinIncome.retryInventory(player);
            State state = SERVERS.remove(server);
            if (state != null && state.sender != null) {
                try { state.sender.close(); }
                catch (IOException ex) { LOGGER.error("[HalloweenOnline] 关闭 outbox 失败；请保留世界 halloween-online 目录恢复"); }
            }
        });
    }

    private static void start(MinecraftServer server) {
        if (SERVERS.containsKey(server)) return;
        State state = new State();
        SERVERS.put(server, state);
        EarningsStore store = null;
        try {
            OnlineConfig config = OnlineConfig.load(FabricLoader.getInstance().getConfigDir().resolve("halloween-online.json"));
            state.enabled = config.enabled;
            if (!config.enabled) {
                LOGGER.info("[HalloweenOnline] 官网南瓜币数据库同步未启用；请配置现有 MySQL 玩家同步及 config/halloween-online.json 的 enabled。启用前收益不补记。");
                return;
            }
            config.validate();
            String table = config.tableName(net.exmo.sre.sync.MysqlPlayerDataStore.tablePrefix());
            store = new EarningsStore(server.getWorldPath(LevelResource.ROOT).resolve("halloween-online"));
            var sink = new JdbcEarningsSink(() -> {
                var source = net.exmo.sre.sync.MysqlPlayerDataStore.getDataSource();
                if (source == null) throw new java.sql.SQLException("Shared game database unavailable");
                return source.getConnection();
            }, table, config.statementTimeoutSeconds, config.createTableIfMissing);
            state.sender = new EarningsSender(store, sink, config,
                    System::currentTimeMillis, () -> ThreadLocalRandom.current().nextLong(), LOGGER::warn);
            state.sender.requestFlush();
            state.sender.start();
            LOGGER.info("[HalloweenOnline] 官网南瓜币数据库同步已启用；历史未确认收益将继续批量写入共享游戏库");
        } catch (IOException | RuntimeException ex) {
            // 不打印配置、URL、异常正文（它们可能包含 secret）。失败关闭，不能结算掉未落盘的凭据。
            state.enabled = true;
            state.sender = null;
            if (store != null) try { store.close(); } catch (IOException ignored) { /* 已有明确故障日志 */ }
            LOGGER.error("[HalloweenOnline] 配置/世界 halloween-online 目录加载失败 ({})；同步失败关闭，请检查配置与 earnings.wal，修复后重启。未落盘收益保留在未结算物品中。",
                    ex.getClass().getSimpleName());
        }
    }

    static boolean shouldRecord(ServerPlayer player) {
        State state = SERVERS.get(player.server);
        return state != null && state.enabled && HalloweenRuntime.isEnabled(player.level());
    }

    static boolean persist(ServerPlayer holder, Earning earning) {
        State state = SERVERS.get(holder.server);
        if (state == null || !state.enabled) return false;
        try {
            if (state.sender == null) throw new IOException("Outbox unavailable");
            state.sender.record(earning);
            return true;
        } catch (IOException | RuntimeException ex) {
            long now = System.currentTimeMillis();
            if (now - state.lastDiskWarning >= 60_000) {
                state.lastDiskWarning = now;
                LOGGER.error("[HalloweenOnline] 收益尚未可靠写盘 ({})；实际南瓜栈保留未结算凭据并重试。请立即检查磁盘/配置，勿清理背包或回滚世界。",
                        ex.getClass().getSimpleName());
            }
            return false;
        }
    }
}
