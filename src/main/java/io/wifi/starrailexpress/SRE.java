package io.wifi.starrailexpress;

import com.google.common.reflect.Reflection;
import com.google.gson.JsonObject;
import io.wifi.ConfigCompact.ConfigEvents;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.api.replay.ReplayApiInitializer;
import io.wifi.starrailexpress.api.replay.ReplayPayload;
import io.wifi.starrailexpress.api.replay.screen.ReplayScreenService;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.block.DoorPartBlock;
import io.wifi.starrailexpress.content.command.*;
import io.wifi.starrailexpress.content.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.content.command.argument.MapLoadArgumentType;
import io.wifi.starrailexpress.content.command.argument.SkinArgumentType;
import io.wifi.starrailexpress.content.command.argument.TimeOfDayArgumentType;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.command.SREVoteCommand;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.event.AFKEventHandler;
import io.wifi.starrailexpress.event.EntityInteractionHandler;
import io.wifi.starrailexpress.event.PlayerInteractionHandler;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.PlayerMountainHandler;
import io.wifi.starrailexpress.game.TeamKillViolationHandler;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeModifiers;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.*;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.network.original.*;
import io.wifi.starrailexpress.network.packet.CustomNarratorPacket;
import io.wifi.starrailexpress.network.packet.ModVersionPacket;
import io.wifi.starrailexpress.network.packet.SyncRoomToPlayerPayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.scenery.network.SceneAssetNetwork;
import io.wifi.starrailexpress.scenery.server.SceneAssetServer;
import io.wifi.starrailexpress.stats.PlayerStatsManager;
import io.wifi.starrailexpress.util.PoisonComponentUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.exmo.sre.mod_whitelist.server.command.ModWhitelistCommand;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.modes.fourthroom.network.*;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SRE extends StarRailExpressID implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static MinecraftServer SERVER;
    public static SREMurderGameMode GAME;
    public static GameReplayManager REPLAY_MANAGER;
    public static final Networking NETWORKING = new Networking();
    public static boolean isLobby = false;
    public static List<Predicate<SRERole>> canUseOtherPerson = new ArrayList<>();
    public static List<Predicate<SRERole>> canUseChatHud = new ArrayList<>();
    public static List<Predicate<Player>> canUseChatHudPlayer = new ArrayList<>();
    public static List<Predicate<Player>> cantUseChatHud = new ArrayList<>();
    public static List<Predicate<Player>> canCollide = new ArrayList<>();
    public static List<Predicate<Entity>> cantPushableBy = new ArrayList<>();
    public static List<Predicate<Entity>> canCollideEntity = new ArrayList<>();
    public static List<Predicate<DeathInfo>> canStickArmor = new ArrayList<>();
    public static List<Predicate<ServerPlayer>> cantSendReplay = new ArrayList<>();
    public static List<Predicate<ServerPlayer>> canSendReplay = new ArrayList<>();

    public static ArrayList<String> canDropItem = new ArrayList<>();
    public static ArrayList<Predicate<Player>> canDrop = new ArrayList<>();

    public static @NotNull ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    public static void SendRoomInfoToPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncRoomToPlayerPayload(GameUtils.roomToPlayer));
    }

    public static boolean canSeeBarrier() {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            return SREClient.canSeeBarrier();
        }
        return false;
    }

    @Override
    public void onInitialize() {
        initConfig();
        initConstants();
        initWaypoints();
        initReplayApi();
        registerEventHandlers();
        registerServerLifecycleEvents();
        initRegistries();
        initNetworkStatistics();
        registerCommandArgumentTypes();
        registerCommands();
        registerServerPlayConnectionEvents();
        PlayerStatsManager.registerEvents();
        PlayerEconomyManager.registerEvents();
        ProgressionDataManager.registerEvents();
        io.wifi.starrailexpress.roster.RoleRosterManager.registerEvents();
        registerPayloadTypes();
        registerGlobalReceivers();
        registerPlayerCopyEvent();
        initScheduler();
        initCCAAuto();
        initSkinsNetworkSync();
        SpecialGameModeRoles.init();
        SpecialGameModeModifiers.init();

    }

    private void initCCAAuto() {
        TMMRoles.addRoleComponents(SREPlayerAFKComponent.KEY);
        TMMRoles.addRoleComponents(DynamicShopComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerPsychoComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerMoodComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerNoteComponent.KEY);
        TMMRoles.addRoleComponents(PandaComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerPoisonComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerShopComponent.KEY);
        TMMRoles.addRoleComponents(ExtraSlotComponent.KEY);
    }

    private void initConfig() {
        ConfigEvents.register();
        SREConfig.HANDLER.nothing();
        HarpyModLoaderConfig.HANDLER.nothing();
        NoellesRolesConfig.HANDLER.nothing();
        StupidExpressConfig.HANDLER.nothing();
    }

    public static void initConstants() {
        GameConstants.init();
    }

    private void initWaypoints() {
        io.wifi.starrailexpress.util.WaypointInitUtil.initialize();
    }

    private void initReplayApi() {
        ReplayApiInitializer.init();
    }

    private void registerEventHandlers() {
        PlayerInteractionHandler.register();
        EntityInteractionHandler.register();
        AFKEventHandler.register();
        PlayerMountainHandler.register();
        // 队友击杀违规检测：短期内多次击杀队友则执行 mcfunction
        TeamKillViolationHandler.registerEvent();

        // 游戏开始：通知客户端（驱动 OnGameStartedClient 事件），并向本局玩家播放默认开场镜头
        io.wifi.starrailexpress.event.OnGameStarted.EVENT.register(serverLevel -> {
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

    private void registerServerLifecycleEvents() {
        // 赞助者 plush 右键打开介绍 GUI 的交互拦截（只需注册一次）
        io.wifi.starrailexpress.sponsor.SponsorIntroEvents.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LOGGER.info("[CONFIG] Sync configs to {}", handler.getPlayer().getName().getString());
            SREConfig.HANDLER.syncToClient(handler.getPlayer());
            StupidExpressConfig.HANDLER.syncToClient(handler.getPlayer());
        });
        // 玩家加入时同步当前赞助者名单
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                io.wifi.starrailexpress.sponsor.SponsorManager.syncTo(handler.getPlayer()));
        EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, pos, isNight) -> {
            if (SREGameWorldComponent.KEY.get(player.level()).isRunning())
                return InteractionResult.SUCCESS;
            return InteractionResult.PASS;
        });
        GameUtils.registerEventForServerTickForDoingResetTasks();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER = server;
            VoteManager.init(server);
            VoteManager.registerEvents(); // 注册JOIN事件
            initConstants();
            GAME = new SREMurderGameMode(SRE.id("murder"));
            ServerMapConfig.getInstance(server);
            net.exmo.sre.client.chat.ChatDialogueManager.getInstance(server);
            REPLAY_MANAGER = new GameReplayManager(server);
            SyncMapConfigPayload.sendToAllPlayers();
            // 加载自定义职业
            try {
                io.wifi.starrailexpress.customrole.CustomRoleLoader.reload(server);
                // 同步自定义职业配置到所有客户端
                CustomRoleServerNetwork.clearCache();
                CustomRoleServerNetwork.syncToAllPlayers(server);
            } catch (Throwable e) {
                LOGGER.error("[CustomRole] Failed to load custom roles on server start", e);
            }
            // 拉取赞助者名单（异步）
            io.wifi.starrailexpress.sponsor.SponsorManager.fetchAsync(server);
        });
        ServerTickEvents.START_SERVER_TICK.register(serv -> {
            io.wifi.starrailexpress.game.voting.MapVotingManager.getInstance().tick();
        });
        ServerTickEvents.END_SERVER_TICK.register(serv -> {
            VoteManager.onServerTick();
            ReplayScreenService.tick(serv);
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

    private void initRegistries() {
        Reflection.initialize(SREDataComponentTypes.class);
        TMMSounds.initialize();
        TMMEntities.initialize();
        TMMBlocks.initialize();
        TMMItems.initialize();
        TMMBlockEntities.initialize();
        TMMParticles.initialize();
        TMMDescItems.register();
    }

    private void initNetworkStatistics() {
        NetworkStatistics.getInstance().initialize();
    }

    private void registerCommandArgumentTypes() {
        ArgumentTypeRegistry.registerArgumentType(id("timeofday"), TimeOfDayArgumentType.class,
                SingletonArgumentInfo.contextFree(TimeOfDayArgumentType::timeofday));
        ArgumentTypeRegistry.registerArgumentType(id("gamemode"), GameModeArgumentType.class,
                SingletonArgumentInfo.contextFree(GameModeArgumentType::gameMode));
        ArgumentTypeRegistry.registerArgumentType(id("skin"), SkinArgumentType.class,
                SingletonArgumentInfo.contextFree(SkinArgumentType::string));
        ArgumentTypeRegistry.registerArgumentType(id("map_load"), MapLoadArgumentType.class,
                SingletonArgumentInfo.contextFree(MapLoadArgumentType::string));
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
			ModWhitelistCommand.registerGlobal(dispatcher);
            SREHelpCommand.register(dispatcher);
            SREVoteCommand.register(dispatcher, registryAccess);
            NarratorCommand.register(dispatcher, registryAccess);
            GiveRoomKeyCommand.register(dispatcher);
            ListRoleInRoundCommand.register(dispatcher);
            StartCommand.register(dispatcher);
            NonOPKickCommand.register(dispatcher, registryAccess);
            StopCommand.register(dispatcher);
            SetVisualCommand.register(dispatcher);
            ForceTeamCommand.register(dispatcher);
            SetTimerCommand.register(dispatcher);
            SetDeathPenaltyCommand.register(dispatcher);
            MoneyCommand.register(dispatcher);
            CustomReplayEventCommand.register(dispatcher, registryAccess);
            ReplayScreenCommand.register(dispatcher);
            SetAutoTrainResetCommand.register(dispatcher);
            SetBoundCommand.register(dispatcher);
            AutoStartCommand.register(dispatcher);
            ParticipationCommand.register(dispatcher);
            AutoShutdownWhenNotRunningCommand.register(dispatcher);
            ConfigCommand.register(dispatcher);
            SwitchMapCommand.register(dispatcher);
            MapManagerCommand.register(dispatcher);
            ReloadReadyAreaCommand.register(dispatcher);
            EntityDataCommand.register(dispatcher);
            MoodChangeCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.MapVoteCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.CreateWaypointCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.ToggleWaypointsCommand.register(dispatcher);
            AFKCommand.register(dispatcher);
            ShowStatsCommand.register(dispatcher);
            ShowSelectedMapUICommand.register(dispatcher);
            NetworkStatsCommand.register(dispatcher);
            FourthRoomCommand.register(dispatcher);
            ReloadMapConfigCommand.register(dispatcher);
            SkinsCommand.register(dispatcher);
            ProgressionCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.RoleRosterCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.PlushCommand.register(dispatcher);
            PlayerInventoryCommand.register(dispatcher);
            ShieldCommand.register(dispatcher);
            StaminaCommand.register(dispatcher);
            SceneCommand.register(dispatcher);
            SceneEventCommand.register(dispatcher);
            SceneTaskCommand.register(dispatcher);
            io.wifi.starrailexpress.content.command.MinigameTaskCommand.register(dispatcher);
            io.wifi.starrailexpress.cca.network.SkinsNetworkSyncCommand.register(dispatcher);
            io.wifi.starrailexpress.customrole.CustomRoleReloadCommand.register(dispatcher);
            // CoinModifier.register(dispatcher, registryAccess);
            net.exmo.sre.nametag.NameTagCommand.register(dispatcher, registryAccess);
            net.exmo.sre.subtitle.SubtitleCommand.register(dispatcher, registryAccess);
            net.exmo.sre.camera.AdvancedCameraCommand.register(dispatcher);
            // io.wifi.starrailexpress.contents.command.UnlockAllRolesCommand.register(dispatcher);
        }));
    }

    private void registerServerPlayConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(handler.player.level());
            if (REPLAY_MANAGER != null) {
                var role = gameWorldComponent.getRole(handler.player);
                if (role != null) {
                    REPLAY_MANAGER.addEvent(GameReplayData.EventType.PLAYER_JOIN, handler.player.getUUID(), null, null,
                            handler.player.getScoreboardName());
                }
            }
            // 同步自定义职业配置给新加入的玩家
            CustomRoleServerNetwork.syncToPlayer(server, handler.player);
            SceneAssetServer.sendCurrentManifest(handler.player);
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
            if (REPLAY_MANAGER != null) {
                var role = gameWorldComponent.getRole(handler.player);
                if (role != null) {
                    REPLAY_MANAGER.addEvent(GameReplayData.EventType.PLAYER_LEAVE, handler.player.getUUID(), null, null,
                            handler.player.getScoreboardName());
                }
            }
        });
    }

    private void registerPayloadTypes() {
        SceneAssetNetwork.registerPayloadTypes();
        // Mod Whitelist Payload
        PayloadTypeRegistry.playS2C().register(VoteSyncS2CPacket.TYPE, VoteSyncS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(VoteCastC2SPacket.TYPE, VoteCastC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.mod_whitelist.common.network.ResourcePackWhitelistPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ResourcePackWhitelistPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.exmo.sre.mod_whitelist.common.network.ShaderPackWhitelistPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ShaderPackWhitelistPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload.ID,
                net.exmo.sre.mod_whitelist.common.network.ModWhitelistConfigPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(ModVersionPacket.ID, ModVersionPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ModVersionPacket.ID, ModVersionPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(CustomNarratorPacket.ID, CustomNarratorPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncRoomToPlayerPayload.ID, SyncRoomToPlayerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncRoomToPlayerPayload.ID, SyncRoomToPlayerPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(IsLobbyConfigPayload.ID, IsLobbyConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(IsLobbyConfigPayload.ID, IsLobbyConfigPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(JoinSpecGroupPayload.ID, JoinSpecGroupPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(JoinSpecGroupPayload.ID, JoinSpecGroupPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(OnGameStartedPayload.TYPE, OnGameStartedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OnGameFinishedPayload.TYPE, OnGameFinishedPayload.CODEC);

        // 高级相机轨道
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.camera.AdvancedCameraPayload.ID,
                net.exmo.sre.camera.AdvancedCameraPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncMapConfigPayload.ID, SyncMapConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerScreenEdgeEffectPayload.ID, TriggerScreenEdgeEffectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSkinSelectedPayload.ID, UpdateSkinSelectedPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateNameTagSelectedPayload.ID, UpdateNameTagSelectedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoveStatusBarPayload.ID, RemoveStatusBarPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerStatusBarPayload.ID, TriggerStatusBarPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BreakArmorPayload.ID, BreakArmorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShootMuzzleS2CPayload.ID, ShootMuzzleS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SniperScopeStateS2CPayload.TYPE,
                SniperScopeStateS2CPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PoisonComponentUtils.PoisonOverlayPayload.ID,
                PoisonComponentUtils.PoisonOverlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GunDropPayload.ID, GunDropPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TaskCompletePayload.ID, TaskCompletePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnnounceWelcomePayload.ID, AnnounceWelcomePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnnounceEndingPayload.ID, AnnounceEndingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReplayPayload.ID, ReplayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SecurityCameraModePayload.ID, SecurityCameraModePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowStatsPayload.ID, ShowStatsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerStatsSyncPayload.ID, PlayerStatsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerDataPartSyncPayload.ID, PlayerDataPartSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.RoleRosterSyncPayload.ID,
                io.wifi.starrailexpress.network.RoleRosterSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.sponsor.SponsorListPayload.ID,
                io.wifi.starrailexpress.sponsor.SponsorListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.OpenRoleRosterScreenPayload.ID,
                io.wifi.starrailexpress.network.OpenRoleRosterScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.network.RoleRosterUpdatePayload.ID,
                io.wifi.starrailexpress.network.RoleRosterUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowSelectedMapUIPayload.ID, ShowSelectedMapUIPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapVotingResultsPayload.TYPE, MapVotingResultsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseUiPayload.ID, CloseUiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerDeathPayload.ID, PlayerDeathPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FourthRoomStatePayload.ID, FourthRoomStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FourthRoomTableEffectsPayload.ID, FourthRoomTableEffectsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenFourthRoomPeekDeckPayload.ID, OpenFourthRoomPeekDeckPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSkinScreenPaylod.ID, OpenSkinScreenPaylod.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenProgressionScreenPayload.ID, OpenProgressionScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenClueArchivePayload.ID, OpenClueArchivePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.OpenRoleUnlockScreenPayload.ID,
                io.wifi.starrailexpress.network.OpenRoleUnlockScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.RoleUnlockedHudPayload.ID,
                io.wifi.starrailexpress.network.RoleUnlockedHudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.network.packet.SyncWaypointsPacket.ID,
                io.wifi.starrailexpress.network.packet.SyncWaypointsPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket.ID,
                io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                io.wifi.starrailexpress.network.packet.SyncSpecificWaypointVisibilityPacket.ID,
                io.wifi.starrailexpress.network.packet.SyncSpecificWaypointVisibilityPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(KnifeStabPayload.ID, KnifeStabPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GunShootPayload.ID, GunShootPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SniperShootPayload.TYPE, SniperShootPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(StoreBuyPayload.ID, StoreBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NoteEditPayload.ID, NoteEditPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestOpenClueArchivePayload.ID, RequestOpenClueArchivePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.network.VoteForMapPayload.ID,
                io.wifi.starrailexpress.network.VoteForMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SecurityCameraExitRequestPayload.ID,
                SecurityCameraExitRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NunchuckHitPayload.ID, NunchuckHitPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CardPlayPayload.ID, CardPlayPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyFourthRoomItemPayload.ID, BuyFourthRoomItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RevealIdentityPayload.ID, RevealIdentityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CompleteFourthRoomTaskPayload.ID, CompleteFourthRoomTaskPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EndTurnPayload.ID, EndTurnPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UseAssassinationItemPayload.ID, UseAssassinationItemPayload.CODEC);

        // Chat Dialogue
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.client.chat.OpenChatDialoguePayload.ID,
                net.exmo.sre.client.chat.OpenChatDialoguePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(net.exmo.sre.client.chat.ChatDialogueAdvancePayload.ID,
                net.exmo.sre.client.chat.ChatDialogueAdvancePayload.CODEC);

        // Subtitle 字幕报幕
        PayloadTypeRegistry.playS2C().register(net.exmo.sre.subtitle.SubtitleS2CPayload.ID,
                net.exmo.sre.subtitle.SubtitleS2CPayload.CODEC);

        // Mailbox
        PayloadTypeRegistry.playS2C().register(io.wifi.starrailexpress.content.mail.OpenMailboxScreenPayload.ID,
                io.wifi.starrailexpress.content.mail.OpenMailboxScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.ID,
                io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.CODEC);

        // 实体交互方块数据包
        PayloadTypeRegistry.playS2C().register(EntityInteractionBlockPayload.OpenUI.TYPE,
                EntityInteractionBlockPayload.OpenUI.CODEC);
        PayloadTypeRegistry.playS2C().register(EntityInteractionBlockPayload.SyncBlockEntity.TYPE,
                EntityInteractionBlockPayload.SyncBlockEntity.CODEC);
        PayloadTypeRegistry.playC2S().register(EntityInteractionBlockPayload.SaveConfig.TYPE,
                EntityInteractionBlockPayload.SaveConfig.CODEC);

        // 小游戏任务点数据包
        PayloadTypeRegistry.playS2C().register(MinigameQuestPayload.OpenConfig.TYPE,
                MinigameQuestPayload.OpenConfig.CODEC);
        PayloadTypeRegistry.playS2C().register(MinigameQuestPayload.OpenGame.TYPE, MinigameQuestPayload.OpenGame.CODEC);
        PayloadTypeRegistry.playC2S().register(MinigameQuestPayload.SaveConfig.TYPE,
                MinigameQuestPayload.SaveConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(MinigameQuestPayload.CompleteGame.TYPE,
                MinigameQuestPayload.CompleteGame.CODEC);

        // 职业轮选数据包
        PayloadTypeRegistry.playC2S().register(RoleRotationSelectC2SPacket.TYPE, RoleRotationSelectC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(RoleRotationSyncS2CPacket.TYPE, RoleRotationSyncS2CPacket.CODEC);
    }

    private void registerGlobalReceivers() {
        SceneAssetNetwork.registerServerReceivers();

        UpdateSkinSelectedPayload.registerReceiver();
        UpdateNameTagSelectedPayload.registerReceiver();
        // 服务端处理客户端投票包
        ServerPlayNetworking.registerGlobalReceiver(VoteCastC2SPacket.TYPE, (packet, context) -> {
            VoteManager.handleVoteCast(context.player(), packet.optionIndices());
        });
        ServerPlayNetworking.registerGlobalReceiver(KnifeStabPayload.ID, new KnifeStabPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(ModVersionPacket.ID, new ModVersionPacket.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(GunShootPayload.ID, new GunShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(SniperShootPayload.TYPE, new SniperShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(StoreBuyPayload.ID, new StoreBuyPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(NoteEditPayload.ID, new NoteEditPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(RequestOpenClueArchivePayload.ID,
                new RequestOpenClueArchivePayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.VoteForMapPayload.ID,
                (payload, context) -> {
                    io.wifi.starrailexpress.network.VoteForMapPayload.Handler.handle(payload, context.player());
                });

        // 实体交互方块服务端网络处理
        EntityInteractionBlockServerNetwork.register();
        MinigameQuestServerNetwork.register();
        // 画板服务端网络处理
        DrawingBoardServerNetwork.register();
        ServerPlayNetworking.registerGlobalReceiver(SecurityCameraExitRequestPayload.ID,
                new SecurityCameraExitRequestPayload.ServerReceiver());
        ServerPlayNetworking.registerGlobalReceiver(JoinSpecGroupPayload.ID, (payload, context) -> {
            joinVoice(payload, context);

        });
        ServerPlayNetworking.registerGlobalReceiver(NunchuckHitPayload.ID, new NunchuckHitPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(CardPlayPayload.ID, new CardPlayPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(BuyFourthRoomItemPayload.ID,
                new BuyFourthRoomItemPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(RevealIdentityPayload.ID, new RevealIdentityPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(CompleteFourthRoomTaskPayload.ID,
                new CompleteFourthRoomTaskPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(EndTurnPayload.ID, new EndTurnPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(UseAssassinationItemPayload.ID,
                new UseAssassinationItemPayload.Receiver());

        // Role Rotation receivers
        RoleRotationSelectC2SPacket.registerServerReceiver();

        // 职业轮换系统：管理员编辑名单
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.RoleRosterUpdatePayload.ID,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> {
                        if (!player.hasPermissions(2)) {
                            return;
                        }
                        switch (payload.action()) {
                            case "set" -> io.wifi.starrailexpress.roster.RoleRosterManager.setFromJson(payload.json());
                            case "enable" -> io.wifi.starrailexpress.roster.RoleRosterManager.setEnabled(true);
                            case "disable" -> io.wifi.starrailexpress.roster.RoleRosterManager.setEnabled(false);
                            case "clear" -> io.wifi.starrailexpress.roster.RoleRosterManager.clear();
                            case "randomize" -> {
                                int count = context.server().getPlayerCount();
                                try {
                                    if (payload.json() != null && !payload.json().isBlank()) {
                                        count = Integer.parseInt(payload.json().trim());
                                    }
                                } catch (NumberFormatException ignored) {
                                    // 使用在线人数
                                }
                                io.wifi.starrailexpress.roster.RoleRosterManager.randomize(Math.max(1, count));
                            }
                            default -> {
                            }
                        }
                    });
                });

        // Mailbox receivers
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailClaimC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailDeleteC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailClaimAllC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailDeleteAllReadC2SPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.ID,
                new io.wifi.starrailexpress.content.mail.MailMarkReadC2SPayload.Receiver());

        // Chat Dialogue advance handler
        ServerPlayNetworking.registerGlobalReceiver(
                net.exmo.sre.client.chat.ChatDialogueAdvancePayload.ID, (payload, context) -> {
                    var mgr = net.exmo.sre.client.chat.ChatDialogueManager
                            .getInstance(context.player().getServer());
                    var data = mgr.get(payload.dialogueId());
                    if (data == null)
                        return;
                    int idx = payload.lineIndex();
                    if (idx < 0 || idx >= data.lines.size())
                        return;
                    var line = data.lines.get(idx);

                    if (payload.choiceIndex() >= 0) {
                        if (!line.hasChoices())
                            return;
                        int choiceIndex = payload.choiceIndex();
                        if (choiceIndex < 0 || choiceIndex >= line.choices.size())
                            return;

                        var choice = line.choices.get(choiceIndex);
                        executeDialogueCommand(context, choice.command, choice.runsOnServer());

                        if (choice.opensDialogue()) {
                            var nextDialogue = mgr.get(choice.nextDialogue);
                            if (nextDialogue != null) {
                                net.exmo.sre.client.chat.OpenChatDialoguePayload.sendToPlayer(
                                        context.player(), nextDialogue, payload.focusEntityId());
                            } else {
                                LOGGER.warn("[SRE-Chat] Missing next dialogue '{}' from '{}' line {} choice {}",
                                        choice.nextDialogue, payload.dialogueId(), idx, choiceIndex);
                            }
                        }
                        return;
                    }

                    executeDialogueCommand(context, line.command, line.runsOnServer());
                });
    }

    private static void executeDialogueCommand(ServerPlayNetworking.Context context, String command,
            boolean runOnServer) {
        if (!runOnServer || command == null || command.isBlank())
            return;
        context.player().getServer().getCommands()
                .performPrefixedCommand(
                        context.player().createCommandSourceStack()
                                .withPermission(2)
                                .withSuppressedOutput(),
                        command);
    }

    private void joinVoice(JoinSpecGroupPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sp = context.player();
        boolean isJoin = payload.isJoin();
        if (isJoin) {
            if (GameUtils.isPlayerSpectator(sp)) {
                TrainVoicePlugin.addPlayer(sp.getUUID());
            }
        } else {
            TrainVoicePlugin.resetPlayer(sp.getUUID());
        }
    }

    private void registerPlayerCopyEvent() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            SyncMapConfigPayload.sendToPlayer(newPlayer);
        });
    }

    private void initScheduler() {
        Scheduler.init();
    }

    /**
     * 初始化皮肤网络同步系统
     */
    private void initSkinsNetworkSync() {
        try {
            io.wifi.starrailexpress.cca.network.SkinsNetworkSyncInitializer.registerEvents();
            // 可以在此配置网络服务器地址
            // SkinsNetworkSyncInitializer.setNetworkServer("localhost", 8888);
            if (!SREConfig.instance().mysqlPlayerSyncEnabled) {
                return;
            }
            LOGGER.info("皮肤网络同步系统已初始化");
        } catch (Exception e) {
            LOGGER.error("初始化皮肤网络同步系统时出错", e);
        }
    }

    public static boolean isSkyVisible(@NotNull Entity player) {
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        boolean canSeeSky = player.level().canSeeSky(eyePos);
        return canSeeSky;
    }

    public static boolean isSkyVisibleAdjacent(@NotNull Entity player) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos playerPos = BlockPos.containing(player.getEyePosition());
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                mutable.set(playerPos.getX() + x, playerPos.getY(), playerPos.getZ() + z);
                final var chunkPos = player.chunkPosition();
                final var chunk = player.level().getChunk(chunkPos.x, chunkPos.z);
                final var i = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING)
                        .getFirstAvailable(mutable.getX() & 15, mutable.getZ() & 15) - 1;
                if (i < player.getY() + 3) {
                    return !(player.level().getBlockState(playerPos).getBlock() instanceof DoorPartBlock);
                }
            }
        }
        return false;
    }

    public static boolean isExposedToWind(@NotNull Entity player) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos playerPos = BlockPos.containing(player.getEyePosition());
        for (int x = 0; x <= 10; x++) {
            mutable.set(playerPos.getX() - x, player.getEyePosition().y(), playerPos.getZ());
            if (!player.level().canSeeSky(mutable)) {
                return false;
            }
        }
        return true;
    }

    public static final ResourceLocation COMMAND_ACCESS = id("commandaccess");

    public static boolean isPlayerInGame(Player player) {
        return GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player);
    }

    public static class Networking {
        public void sendToAllPlayers(CustomPacketPayload packet) {
            if (SERVER != null) {
                for (ServerPlayer player : SERVER.getPlayerList().getPlayers()) {
                    PacketTracker.sendToClient(player, packet);
                }
            }
        }
    }
}
