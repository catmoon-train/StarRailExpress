package io.wifi.starrailexpress;

import com.google.common.reflect.Reflection;
import io.wifi.ConfigCompact.ConfigEvents;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.api.replay.ReplayApiInitializer;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block.DoorPartBlock;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeModifiers;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.*;
import io.wifi.starrailexpress.network.*;
import io.wifi.starrailexpress.network.packet.SyncRoomToPlayerPayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.register.SRECommandRegister;
import io.wifi.starrailexpress.register.SREEventRegister;
import io.wifi.starrailexpress.register.SREPayloadRegister;
import org.agmas.noellesroles.util.DeathReasonRegistry;
import io.wifi.starrailexpress.register.SREReceiverRegister;
import io.wifi.starrailexpress.stats.PlayerStatsManager;
import io.wifi.starrailexpress.util.Scheduler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

public class SRE extends StarRailExpressID implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static MinecraftServer SERVER;
    public static SREMurderGameMode GAME;
    public static GameReplayManager REPLAY_MANAGER;
    public static final Networking NETWORKING = new Networking();
    public static boolean isLobby = false;
    // 各类"列表式"注册点已按类别归一化至 io.wifi.starrailexpress.rules 包：
    //   ChatHudRules        - canUseChatHud / canUseChatHudPlayer / cantUseChatHud
    //   ReplayRules         - canSendReplay / cantSendReplay
    //   CollisionRules      - canCollide / cantPushableBy / canCollideEntity
    //   ArmorRules          - canStickArmor
    //   DropRules           - canDropItem / canDrop
    //   RoleVisibilityRules - canUseOtherPerson

    public static @NotNull ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    public static @NotNull ResourceLocation deathReason(String name, DeathReasonRegistry.KillUsage... usages) {
        return DeathReasonRegistry.register(id(name), usages);
    }

    public static @NotNull ResourceLocation forceDeathReason(String name) {
        return DeathReasonRegistry.force(id(name));
    }

    public static @NotNull ResourceLocation bothDeathReason(String name) {
        return DeathReasonRegistry.both(id(name));
    }

    public static @NotNull ResourceLocation wifiDeathReason(String name, DeathReasonRegistry.KillUsage... usages) {
        return DeathReasonRegistry.register(wifiId(name), usages);
    }

    public static @NotNull ResourceLocation bothWifiDeathReason(String name) {
        return DeathReasonRegistry.both(wifiId(name));
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
        SREEventRegister.registerEventHandlers();
        SREEventRegister.registerServerLifecycleEvents();
        initRegistries();
        initNetworkStatistics();
        SRECommandRegister.registerCommandArgumentTypes();
        SRECommandRegister.registerCommands();
        SREEventRegister.registerServerPlayConnectionEvents();
        PlayerStatsManager.registerEvents();
        PlayerEconomyManager.registerEvents();
        ProgressionDataManager.registerEvents();
        io.wifi.starrailexpress.backpack.BackpackManager.registerEvents();
        io.wifi.starrailexpress.roster.RoleRosterManager.registerEvents();
        SREPayloadRegister.registerPayloadTypes();
        SREReceiverRegister.registerGlobalReceivers();
        SREEventRegister.registerPlayerCopyEvent();
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
