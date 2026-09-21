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

package io.wifi.starrailexpress.register;

import org.agmas.noellesroles.init.RoleShopHandler;

import com.google.gson.JsonObject;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
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
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationGameMode;
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationSingleSelectGameMode;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.scenery.server.SceneAssetServer;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
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
        io.wifi.starrailexpress.game.modes.funny.mob.MobRiotRules.registerEvents();
        registerRoleEventEnableHandlers();

        // 游戏开始：通知客户端（驱动 OnGameStartedClient 事件），并向本局玩家播放默认开场镜头
        net.exmo.sre.planecrash.PlaneCrashManager.register();

        OnGameEnd.EVENT.register((serverLevel, __cca) -> {
            RefugeeComponent.KEY.get(serverLevel).clear();
            for (ServerPlayer player : serverLevel.players()) {
                PacketTracker.sendToClient(player, new OnGameFinishedPayload());
            }
        });
        OnGameStarted.EVENT.register(serverLevel -> {
            RefugeeComponent.KEY.get(serverLevel).clear();
            boolean deferIntro = defersIntroUntilRolesChosen(serverLevel);
            String mapId = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(serverLevel).mapName;
            net.exmo.sre.planecrash.PlaneCrashManager.onGameStarted(serverLevel);
            boolean planeIntro = !deferIntro && net.exmo.sre.planecrash.PlaneCrashManager.startIntro(serverLevel);
            for (ServerPlayer player : serverLevel.players()) {
                // The authoritative map id also drives the opening briefing when no map vote was used.
                PacketTracker.sendToClient(player, new OnGameStartedPayload(mapId));
                // 轮选模式职业尚未确定，开场镜头延后到 OnGameTrueStarted
                if (!deferIntro && !planeIntro) {
                    sendDefaultIntroIfParticipant(player);
                }
            }
        });
        OnGameTrueStarted.EVENT.register(serverLevel -> {
            if (!defersIntroUntilRolesChosen(serverLevel)) {
                return;
            }
            if (net.exmo.sre.planecrash.PlaneCrashManager.startIntro(serverLevel)) {
                return;
            }
            for (ServerPlayer player : serverLevel.players()) {
                sendDefaultIntroIfParticipant(player);
            }
        });
        // 开局时按最新索引重建自定义职业的商店（与 RoleShopHandler.shopRegister() 在开局重注册
        // 内置职业商店条目同理）：职业内容与自定义物品内容这时都已经加载 / 同步完毕，重建一次能兜住
        // 「注册职业时物品索引还没就绪」的情况。只换商店列表，不重新注册职业实例。
        OnGameStarted.EVENT.register(serverLevel -> {
            if (io.wifi.starrailexpress.customrole.CustomRoleLoader.rebuildShops() > 0) {
                // 商店条目变了：价格表是按商店内容构建并缓存的，让客户端也重新拉一次
                io.wifi.starrailexpress.shop.ShopPriceSyncServer.resyncAll(serverLevel.getServer());
            }
        });
    }

    /**
     * 职业随机事件（{@link SRERole#setEventEnableChance(java.util.function.BiConsumer, int)}）的开局掷骰与结束清理。
     * <p>
     * 监听器只在这里注册一次，派发目标由 {@code TMMRoles} 维护的「声明过事件的职业」列表决定
     * （见 {@link SRERole#rollAllEventEnableChances}）：未声明事件的职业不参与，
     * 职业被注销时会自动移出列表，不会残留指向旧实例的监听器。
     */
    private static void registerRoleEventEnableHandlers() {
        OnGameTrueStarted.EVENT.register(SRERole::rollAllEventEnableChances);
        OnGameEnd.EVENT.register((serverLevel, __cca) -> SRERole.resetAllEventEnableStates(serverLevel));
    }

    public static void registerServerLifecycleEvents() {
        // 赞助者 plush 右键打开介绍 GUI 的交互拦截（只需注册一次）
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

        VoteManager.registerEvents(); // 注册JOIN事件
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SRE.SERVER = server;
            VoteManager.init(server);
            RoleShopHandler.shopRegister();
            SRE.initConstants();
            SRE.GAME = new SREMurderGameMode(SRE.id("murder"));
            ServerMapConfig.getInstance(server);
            net.exmo.sre.client.chat.ChatDialogueManager.getInstance(server);
            SRE.REPLAY_MANAGER = new GameReplayManager(server);
            SyncMapConfigPayload.sendToAllPlayers();
            // 加载全部自定义内容：物品 → 方块 → 职业 → 修饰符。顺序由
            // CustomContentReload / ContentChannel.LOAD_ORDER 统一决定——职业的初始物品 / 任务奖励 /
            // 商店条目注册时按 id 查自定义物品索引，必须在物品索引就绪之后再解析（顺序错了会静默
            // 丢掉商店条目）。单项失败不影响其余项。
            io.wifi.starrailexpress.customcontent.CustomContentReload.all(server);
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
            // 自定义内容（职业 / 修饰符 / 列车物品 / 方块）统一发一次握手：
            // 只带各通道的哈希，客户端本地缓存命中就什么都不用发，未命中才回来请求完整内容
            io.wifi.starrailexpress.synccontent.ContentSyncServer.handshakeTo(handler.player);
            SceneAssetServer.sendCurrentManifest(handler.player);
            // 同步当前路径点给新加入的玩家
            io.wifi.starrailexpress.util.WaypointSync.syncTo(handler.player);
            // 商店价格同步握手（仅哈希；客户端本地命中则无需服务端再发完整内容）
            io.wifi.starrailexpress.shop.ShopPriceSyncServer.syncTo(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            io.wifi.starrailexpress.anticheat.ClickAntiCheat.onPlayerDisconnect(handler.player.getUUID());
            // 自定义内容的按玩家运行时状态：物品的命中计数 / 蓄力 / 自动开火窗口，方块的冷却与一次性记录
            io.wifi.starrailexpress.customitem.CustomItemRuntime.clearPlayer(handler.player.getUUID());
            io.wifi.starrailexpress.customblock.CustomBlockRuntime.clearPlayer(handler.player.getUUID());
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

    /** 轮选（闪电轮抽 / 单选）在 OnGameStarted 时职业尚未确定，开场镜头应延后。 */
    private static boolean defersIntroUntilRolesChosen(ServerLevel serverLevel) {
        var mode = SREGameWorldComponent.KEY.get(serverLevel).getGameMode();
        return mode instanceof SRERoleRotationGameMode
                || mode instanceof SRERoleRotationSingleSelectGameMode;
    }

    private static void sendDefaultIntroIfParticipant(ServerPlayer player) {
        if (player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            net.exmo.sre.camera.AdvancedCameraCommand.sendIntro(player,
                    net.exmo.sre.camera.AdvancedCameraCommand.DEFAULT_INTRO_DURATION,
                    net.exmo.sre.camera.AdvancedCameraCommand.DEFAULT_INTRO_DISTANCE,
                    net.exmo.sre.camera.AdvancedCameraCommand.DEFAULT_INTRO_HEIGHT);
        }
    }
}
