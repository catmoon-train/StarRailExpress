package io.wifi.starrailexpress;

import com.google.common.reflect.Reflection;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.api.replay.ReplayApiInitializer;
import io.wifi.starrailexpress.api.replay.ReplayPayload;
import io.wifi.starrailexpress.block.DoorPartBlock;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.command.*;
import io.wifi.starrailexpress.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.command.argument.MapLoadArgumentType;
import io.wifi.starrailexpress.command.argument.SkinArgumentType;
import io.wifi.starrailexpress.command.argument.TimeOfDayArgumentType;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.data.ServerMapConfig;
import io.wifi.starrailexpress.event.AFKEventHandler;
import io.wifi.starrailexpress.event.EntityInteractionHandler;
import io.wifi.starrailexpress.event.PlayerInteractionHandler;
import io.wifi.starrailexpress.game.*;
import io.wifi.starrailexpress.index.*;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.network.original.AnnounceEndingPayload;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.network.original.GunDropPayload;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import io.wifi.starrailexpress.network.original.NoteEditPayload;
import io.wifi.starrailexpress.network.original.NunchuckHitPayload;
import io.wifi.starrailexpress.network.original.ShootMuzzleS2CPayload;
import io.wifi.starrailexpress.network.original.SniperScopeStateS2CPayload;
import io.wifi.starrailexpress.network.original.SniperShootPayload;
import io.wifi.starrailexpress.network.original.StoreBuyPayload;
import io.wifi.starrailexpress.network.original.TaskCompletePayload;
import io.wifi.starrailexpress.network.packet.ModVersionPacket;
import io.wifi.starrailexpress.network.packet.SyncRoomToPlayerPayload;
import io.wifi.starrailexpress.util.*;
import dev.upcraft.datasync.api.util.Entitlements;
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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class SRE extends StarRailExpressID implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static MinecraftServer SERVER;
    public static StarRailMurderGameMode GAME;
    public static SREConfig CONFIG = new SREConfig();
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

    public static ArrayList<String> canDropItem = new ArrayList<>();
    public static ArrayList<Predicate<Player>> canDrop = new ArrayList<>();

    public static @NotNull ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    public static void SendRoomInfoToPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncRoomToPlayerPayload(GameUtils.roomToPlayer));
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
        registerPayloadTypes();
        registerGlobalReceivers();
        registerPlayerCopyEvent();
        initScheduler();
        initCCAAuto();
        initSkinsNetworkSync();
    }

    private void initCCAAuto() {
        TMMRoles.addRoleComponents(SREPlayerAFKComponent.KEY);
        TMMRoles.addRoleComponents(DynamicShopComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerPsychoComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerMoodComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerNoteComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerPoisonComponent.KEY);
        TMMRoles.addRoleComponents(SREPlayerShopComponent.KEY);
    }

    private void initConfig() {
        SREConfig.init();
    }

    private void initConstants() {
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
    }

    private void registerServerLifecycleEvents() {
        EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, pos, isNight) -> {
            if (SREGameWorldComponent.KEY.get(player.level()).isRunning())
                return InteractionResult.SUCCESS;
            return InteractionResult.PASS;
        });
        GameUtils.registerEventForServerTickForDoingResetTasks();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER = server;
            GAME = new StarRailMurderGameMode(SRE.id("murder"));
            ServerMapConfig.getInstance(server);
            ServerTickEvents.START_SERVER_TICK.register(serv -> {
                io.wifi.starrailexpress.voting.MapVotingManager.getInstance().tick();
            });
            REPLAY_MANAGER = new GameReplayManager(server);
            SyncMapConfigPayload.sendToAllPlayers();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SRE.isLobby = SREConfig.isLobby;
            sender.sendPacket(new IsLobbyConfigPayload(SRE.isLobby));
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
            GiveRoomKeyCommand.register(dispatcher);
            ListRoleInRoundCommand.register(dispatcher);
            StartCommand.register(dispatcher);
            StopCommand.register(dispatcher);
            SetVisualCommand.register(dispatcher);
            ForceTeamCommand.register(dispatcher);
            SetTimerCommand.register(dispatcher);
            MoneyCommand.register(dispatcher);
            CustomReplayEventCommand.register(dispatcher, registryAccess);
            SetAutoTrainResetCommand.register(dispatcher);
            SetBoundCommand.register(dispatcher);
            AutoStartCommand.register(dispatcher);
            LockToSupportersCommand.register(dispatcher);
            SetRoleCountCommand.register(dispatcher);
            ConfigCommand.register(dispatcher);
            SwitchMapCommand.register(dispatcher);
            UpdateDoorsCommand.register(dispatcher);
            ReloadReadyAreaCommand.register(dispatcher);
            EntityDataCommand.register(dispatcher);
            MoodChangeCommand.register(dispatcher);
            io.wifi.starrailexpress.command.MapVoteCommand.register(dispatcher);
            io.wifi.starrailexpress.command.CreateWaypointCommand.register(dispatcher);
            io.wifi.starrailexpress.command.ToggleWaypointsCommand.register(dispatcher);
            AFKCommand.register(dispatcher);
            ShowStatsCommand.register(dispatcher);
            ShowSelectedMapUICommand.register(dispatcher);
            NetworkStatsCommand.register(dispatcher);
            ReloadMapConfigCommand.register(dispatcher);
            SkinsCommand.register(dispatcher);
            ManageSkinsCommand.register(dispatcher, registryAccess);
            io.wifi.starrailexpress.cca.network.SkinsNetworkSyncCommand.register(dispatcher);
            net.exmo.sre.nametag.NameTagCommand.register(dispatcher);
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
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(handler.player.level());
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
        // Mod Whitelist Payload
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.ID, SyncConfigPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(
                io.wifi.starrailexpress.mod_whitelist.common.network.ModWhitelistPayload.ID,
                io.wifi.starrailexpress.mod_whitelist.common.network.ModWhitelistPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(ModVersionPacket.ID, ModVersionPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ModVersionPacket.ID, ModVersionPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncRoomToPlayerPayload.ID, SyncRoomToPlayerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncRoomToPlayerPayload.ID, SyncRoomToPlayerPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(IsLobbyConfigPayload.ID, IsLobbyConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(IsLobbyConfigPayload.ID, IsLobbyConfigPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(JoinSpecGroupPayload.ID, JoinSpecGroupPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(JoinSpecGroupPayload.ID, JoinSpecGroupPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncMapConfigPayload.ID, SyncMapConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TriggerScreenEdgeEffectPayload.ID, TriggerScreenEdgeEffectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSkinSelectedPayload.ID, UpdateSkinSelectedPayload.CODEC);
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
        PayloadTypeRegistry.playS2C().register(ShowSelectedMapUIPayload.ID, ShowSelectedMapUIPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapVotingResultsPayload.TYPE, MapVotingResultsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseUiPayload.ID, CloseUiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSkinScreenPaylod.ID, OpenSkinScreenPaylod.CODEC);
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
        PayloadTypeRegistry.playC2S().register(io.wifi.starrailexpress.network.VoteForMapPayload.ID,
                io.wifi.starrailexpress.network.VoteForMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SecurityCameraExitRequestPayload.ID,
                SecurityCameraExitRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(NunchuckHitPayload.ID, NunchuckHitPayload.CODEC);
    }

    private void registerGlobalReceivers() {
        UpdateSkinSelectedPayload.registerReceiver();
        ServerPlayNetworking.registerGlobalReceiver(KnifeStabPayload.ID, new KnifeStabPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(ModVersionPacket.ID, new ModVersionPacket.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(GunShootPayload.ID, new GunShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(SniperShootPayload.TYPE, new SniperShootPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(StoreBuyPayload.ID, new StoreBuyPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(NoteEditPayload.ID, new NoteEditPayload.Receiver());
        ServerPlayNetworking.registerGlobalReceiver(io.wifi.starrailexpress.network.VoteForMapPayload.ID,
                (payload, context) -> {
                    io.wifi.starrailexpress.network.VoteForMapPayload.Handler.handle(payload, context.player());
                });
        ServerPlayNetworking.registerGlobalReceiver(SecurityCameraExitRequestPayload.ID,
                new SecurityCameraExitRequestPayload.ServerReceiver());
        ServerPlayNetworking.registerGlobalReceiver(JoinSpecGroupPayload.ID, (payload, context) -> {
            joinVoice(payload, context);

        });
        ServerPlayNetworking.registerGlobalReceiver(NunchuckHitPayload.ID, new NunchuckHitPayload.Receiver());
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
            LOGGER.info("皮肤网络同步系统已初始化");
        } catch (Exception e) {
            LOGGER.error("初始化皮肤网络同步系统时出错", e);
        }
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

    public static @NotNull Boolean isSupporter(Player player) {
        Optional<Entitlements> entitlements = Entitlements.token().get(player.getUUID());
        return entitlements
                .map(value -> value.keys().stream().anyMatch(identifier -> identifier.equals(COMMAND_ACCESS)))
                .orElse(false);
    }

    public static boolean isPlayerInGame(Player player) {
        return GameUtils.isPlayerAliveAndSurvival(player);
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
