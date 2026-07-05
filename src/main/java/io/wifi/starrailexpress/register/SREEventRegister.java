package io.wifi.starrailexpress.register;

import com.google.gson.JsonObject;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.api.replay.board.ReplayBoardService;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.event.*;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.PlayerMountainHandler;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.scenery.server.SceneAssetServer;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

/**
 * 游戏事件、服务器生命周期与玩家连接相关事件注册，
 * 从 {@link SRE#onInitialize()} 中按类别剥离归一化而来。
 */
public class SREEventRegister {

    public static void registerEventHandlers() {
        PlayerInteractionHandler.register();
        EntityInteractionHandler.register();
        AFKEventHandler.register();
        PlayerMountainHandler.register();

        // 游戏开始：通知客户端（驱动 OnGameStartedClient 事件），并向本局玩家播放默认开场镜头

        OnGameEnd.EVENT.register((serverLevel, __cca) -> {
            RefugeeComponent.KEY.get(serverLevel).clear();
        });
        OnGameStarted.EVENT.register(serverLevel -> {
            RefugeeComponent.KEY.get(serverLevel).clear();
            for (ServerPlayer player : serverLevel.players()) {
                PacketTracker.sendToClient(player, new OnGameStartedPayload());
                // 仅向本局参与者（冒险模式）播放"由远及近到玩家位置"的开场镜头
                if (player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.ADVENTURE) {
                    net.exmo.sre.camera.AdvancedCameraCommand.sendIntro(player,
                            net.exmo.sre.camera.AdvancedCameraCommand.DEFAULT_INTRO_DURATION,
                            net.exmo.sre.camera.AdvancedCameraCommand.DEFAULT_INTRO_DISTANCE,
                            net.exmo.sre.camera.AdvancedCameraCommand.DEFAULT_INTRO_HEIGHT);
                }
            }
        });
    }

    public static void registerServerLifecycleEvents() {
        // 赞助者 plush 右键打开介绍 GUI 的交互拦截（只需注册一次）
        io.wifi.starrailexpress.sponsor.SponsorIntroEvents.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SRE.LOGGER.info("[CONFIG] Sync configs to {}", handler.getPlayer().getName().getString());
            SREConfig.HANDLER.syncToClient(handler.getPlayer());
            StupidExpressConfig.HANDLER.syncToClient(handler.getPlayer());
        });
        // 玩家加入时同步当前赞助者名单
        ServerPlayConnectionEvents.JOIN.register((handler, sender,
                server) -> io.wifi.starrailexpress.sponsor.SponsorManager.syncTo(handler.getPlayer()));
        EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, pos, isNight) -> {
            if (SREGameWorldComponent.KEY.get(player.level()).isRunning())
                return InteractionResult.SUCCESS;
            return InteractionResult.PASS;
        });
        GameUtils.registerEventForServerTickForDoingResetTasks();
        ServerLifecycleEvents.SERVER_STOPPED.register((server) -> {
            SRE.SERVER = null;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SRE.SERVER = server;
            VoteManager.init(server);
            VoteManager.registerEvents(); // 注册JOIN事件
            SRE.initConstants();
            SRE.GAME = new SREMurderGameMode(SRE.id("murder"));
            ServerMapConfig.getInstance(server);
            net.exmo.sre.client.chat.ChatDialogueManager.getInstance(server);
            SRE.REPLAY_MANAGER = new GameReplayManager(server);
            SyncMapConfigPayload.sendToAllPlayers();
            // 加载自定义职业
            try {
                io.wifi.starrailexpress.customrole.CustomRoleLoader.reload(server);
                // 同步自定义职业配置到所有客户端
                CustomRoleServerNetwork.clearCache();
                CustomRoleServerNetwork.syncToAllPlayers(server);
            } catch (Throwable e) {
                SRE.LOGGER.error("[CustomRole] Failed to load custom roles on server start", e);
            }
            // 拉取赞助者名单（异步）
            io.wifi.starrailexpress.sponsor.SponsorManager.fetchAsync(server);
        });
        ServerTickEvents.START_SERVER_TICK.register(serv -> {
            io.wifi.starrailexpress.game.voting.MapVotingManager.getInstance().tick();
        });
        ServerTickEvents.END_SERVER_TICK.register(serv -> {
            VoteManager.onServerTick();
            ReplayBoardService.tick(serv);
            SceneAssetServer.tick(serv);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SRE.isLobby = SREConfig.instance().isLobby;
            sender.sendPacket(new IsLobbyConfigPayload(SRE.isLobby));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", player.getUUID().toString());
            obj.addProperty("username", player.getGameProfile().getName());
            MysqlPlayerDataStore.saveBatchForceAsync(
                    player.getUUID(),
                    java.util.Map.of("player_identity", obj.toString()),
                    System.currentTimeMillis());
        });
    }

    public static void registerServerPlayConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(handler.player.level());
            if (SRE.REPLAY_MANAGER != null) {
                var role = gameWorldComponent.getRole(handler.player);
                if (role != null) {
                    SRE.REPLAY_MANAGER.addEvent(GameReplayData.EventType.PLAYER_JOIN, handler.player.getUUID(), null,
                            null,
                            handler.player.getScoreboardName());
                }
            }
            // 同步自定义职业配置给新加入的玩家
            CustomRoleServerNetwork.syncToPlayer(server, handler.player);
            SceneAssetServer.sendCurrentManifest(handler.player);
            // 同步当前路径点给新加入的玩家
            io.wifi.starrailexpress.util.WaypointSync.syncTo(handler.player);
            // 商店价格同步握手（仅哈希；客户端本地命中则无需服务端再发完整内容）
            io.wifi.starrailexpress.shop.ShopPriceSyncServer.syncTo(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CustomRoleServerNetwork.onPlayerDisconnect(handler.player.getUUID());
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(handler.player.level());
            var psychocca = SREPlayerPsychoComponent.KEY.get(handler.player);
            if (psychocca.psychoTicks > 0) {
                psychocca.stopPsychoAndRefreshPsychoCount(true);
                psychocca.sync();
            }
            var rfcca = RefugeeComponent.KEY.get(handler.player.level());
            if (rfcca.isAnyRevivals) {
                if (rfcca.players_stats.containsKey(handler.player.getUUID())) {
                    rfcca.players_stats.remove(handler.player.getUUID());
                }
            }
            if (SRE.REPLAY_MANAGER != null) {
                var role = gameWorldComponent.getRole(handler.player);
                if (role != null) {
                    SRE.REPLAY_MANAGER.addEvent(GameReplayData.EventType.PLAYER_LEAVE, handler.player.getUUID(), null,
                            null,
                            handler.player.getScoreboardName());
                }
            }
        });
    }

    public static void registerPlayerCopyEvent() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            SyncMapConfigPayload.sendToPlayer(newPlayer);
        });
    }
}
