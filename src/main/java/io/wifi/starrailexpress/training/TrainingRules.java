package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 训练身份在加入前确定；专服只接受 Proxy 显式启用，每位真人独占一个维度租约。 */
public final class TrainingRules {
    private static volatile MinecraftServer isolatedServer;
    private static boolean dedicated;
    private static TrainingSession session;
    private static TrainingCampPool pool;
    private static TrainingCampWorlds worlds;
    private static final Map<UUID, Camp> camps = new LinkedHashMap<>();
    private static boolean registered;
    private static boolean failed;
    private TrainingRules() { }

    /** 由 TRAINING 桥接在 SERVER_STARTING 调用；普通专服不能通过客户端包启用。 */
    public static void enableDedicated(MinecraftServer server) {
        if (!server.isDedicatedServer() || !FabricLoader.getInstance().isModLoaded("carpet"))
            throw new IllegalStateException("Dedicated training requires a dedicated server and Carpet");
        if (isolatedServer != null && isolatedServer != server)
            throw new IllegalStateException("Another training server is active");
        isolatedServer = server;
        dedicated = true;
        failed = false;
        if (pool == null) pool = new TrainingCampPool();
    }

    public static void register() {
        if (registered) return;
        registered = true;
        SREGameModes.registerGameMode(new TrainingGameMode());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (!server.isDedicatedServer() && TrainingWorldInstaller.isTrainingWorld(server.getWorldPath(LevelResource.ROOT))) {
                isolatedServer = server; dedicated = false; failed = false;
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!isTrainingServer(server)) return;
            var game = SREGameWorldComponent.KEY.get(server.overworld());
            game.setGameStatus(SREGameWorldComponent.GameStatus.INACTIVE);
            game.setGameMode(SREGameModes.GAME_MODES.get(TrainingGameMode.ID));
            if (dedicated) {
                try {
                    worlds = new TrainingCampWorlds(server);
                    for (int slot = 0; slot < TrainingCampPool.CAPACITY; slot++)
                        if (server.getLevel(TrainingCampWorlds.dimension(slot)) == null)
                            throw new IllegalStateException("Missing training camp dimension " + slot);
                    worlds.ensureMapConfiguration();
                }
                catch (RuntimeException e) { failed = true; throw new IllegalStateException("训练营世界初始化失败，拒绝启动 TRAINING 节点", e); }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (isDedicatedTrainingServer(server) && !isManagedBot(handler.player)) join(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!isDedicatedTrainingServer(server)) return;
            Camp camp = camps.get(handler.player.getUUID());
            // 旧连接的延迟断开不能关闭同 UUID 的新会话。
            if (camp != null && camp.player.connection == handler) retire(camp);
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                !level.isClientSide && player instanceof ServerPlayer actor && !canInteract(actor, target)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) ->
                !(source.getEntity() instanceof ServerPlayer actor) || canInteract(actor, target));
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (isDedicatedTrainingServer(server)) {
                for (Camp camp : new ArrayList<>(camps.values())) confine(camp);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!isTrainingServer(server)) return;
            if (dedicated) { tickDedicated(server); return; }
            if (server.isPublished()) { if (session != null) { session.close(); session = null; } return; }
            if (session == null && !failed && server.getTickCount() >= 20) {
                var owner = server.getPlayerList().getPlayers().stream().filter(p -> server.isSingleplayerOwner(p.getGameProfile())).findFirst().orElse(null);
                if (owner == null) return;
                if (!FabricLoader.getInstance().isModLoaded("carpet")) {
                    owner.sendSystemMessage(Component.translatable("training.sre.error.carpet")); failed = true; return;
                }
                try { session = new TrainingSession(server, owner); }
                catch (RuntimeException e) {
                    failed = true; SRE.LOGGER.error("训练场初始化失败", e);
                    SREGameWorldComponent.KEY.get(server.overworld()).setGameStatus(SREGameWorldComponent.GameStatus.INACTIVE);
                    owner.sendSystemMessage(Component.translatable("training.sre.error.initialize"));
                }
            }
            if (session != null) session.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (!isTrainingServer(server)) return;
            if (session != null) { session.close(); session = null; }
            for (Camp camp : new ArrayList<>(camps.values())) retire(camp);
            if (worlds != null) worlds.close();
            if (pool != null) pool.close();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (!isTrainingServer(server)) return;
            isolatedServer = null; session = null; worlds = null; pool = null;
            camps.clear(); dedicated = false; failed = false;
        });
    }

    private static void join(ServerPlayer player) {
        if (failed || worlds == null || pool == null) { reject(player, "training.sre.error.initialize"); return; }
        Camp existing = camps.get(player.getUUID());
        if (existing != null) {
            if (existing.player == player) return;
            retire(existing);
            if (camps.containsKey(player.getUUID())) { reject(player, "training.sre.error.initialize"); return; }
        }
        var lease = pool.acquire(player.getUUID());
        if (lease == null) { reject(player, "training.sre.error.full"); return; }
        Camp camp = new Camp(lease, player);
        camps.put(player.getUUID(), camp);
        try {
            if (player.getServer().getLevel(TrainingCampWorlds.dimension(lease.slot())) == null)
                throw new IllegalStateException("Missing training dimension " + lease.slot());
            confine(camp);
            worlds.prepare(lease.slot());
        } catch (RuntimeException e) {
            SRE.LOGGER.error("无法分配独立训练营", e);
            reject(player, "training.sre.error.initialize"); retire(camp);
        }
    }

    private static void confine(Camp camp) {
        var player = camp.player;
        var level = player.getServer().getLevel(TrainingCampWorlds.dimension(camp.lease.slot()));
        if (level == null) return;
        var spawn = TrainingLayout.get().area("lobby").spawn();
        double y = camp.session == null ? 130 : spawn.y;
        if (camp.session == null) player.setGameMode(GameType.SPECTATOR);
        if (player.serverLevel() != level || camp.session == null && player.position().distanceToSqr(spawn.x, y, spawn.z) > 16) {
            player.stopRiding(); player.teleportTo(level, spawn.x, y, spawn.z, 0, 0);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); player.fallDistance = 0;
        }
    }

    private static void tickDedicated(MinecraftServer server) {
        for (Camp camp : new ArrayList<>(camps.values())) {
            ServerPlayer current = server.getPlayerList().getPlayer(camp.lease.owner());
            if (current == null || current.connection != camp.player.connection) retire(camp);
            else camp.player = current;
        }
        // 快照迭代，Carpet 加入/离开不影响真人分配；容量还由本地池再次硬性限制。
        for (var player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (!isManagedBot(player) && !camps.containsKey(player.getUUID())) join(player);
        }
        if (failed || worlds == null) return;
        try { worlds.tick(); }
        catch (RuntimeException e) {
            failed = true; SRE.LOGGER.error("训练营建设失败，拒绝使用未完成地图", e);
            for (Camp camp : new ArrayList<>(camps.values())) { reject(camp.player, "training.sre.error.initialize"); retire(camp); }
            return;
        }
        for (Camp camp : new ArrayList<>(camps.values())) {
            try {
                if (camp.session == null) {
                    if (!worlds.prepare(camp.lease.slot())) {
                        confine(camp);
                        if (server.getTickCount() % 40 == 0) camp.player.displayClientMessage(
                                Component.translatable("training.sre.status.building", worlds.progress(camp.lease.slot())), true);
                        continue;
                    }
                    camp.session = new TrainingSession(server, camp.player,
                            server.getLevel(TrainingCampWorlds.dimension(camp.lease.slot())), true);
                    camp.session.start(camp.player);
                }
                camp.session.tick();
            } catch (RuntimeException e) {
                SRE.LOGGER.error("独立训练会话失败: {}", camp.lease.owner(), e);
                reject(camp.player, "training.sre.error.initialize"); retire(camp);
            }
        }
        GameUtils.isGameStarted = camps.values().stream().anyMatch(c -> c.session != null);
    }

    private static void reject(ServerPlayer player, String key) { player.connection.disconnect(Component.translatable(key)); }
    private static void retire(Camp camp) {
        if (camps.get(camp.lease.owner()) != camp) return;
        try {
            if (camp.session != null) camp.session.close();
            if (worlds != null) worlds.release(camp.lease.slot());
            if (pool != null && !pool.release(camp.lease)) throw new IllegalStateException("Stale training camp lease");
            camps.remove(camp.lease.owner(), camp);
        } catch (RuntimeException e) {
            // 清理失败时不释放场地，宁可暂时少一个槽，也不能把未隔离的场地交给下一位玩家。
            SRE.LOGGER.error("训练营清理失败，保留独占租约: {}", camp.lease, e);
        }
    }

    public static boolean isIsolated() { return isolatedServer != null; }
    public static boolean isTrainingServer(MinecraftServer server) { return server != null && server == isolatedServer; }
    public static boolean isDedicatedTrainingServer(MinecraftServer server) { return dedicated && isTrainingServer(server); }
    public static boolean isTrainingWorld(Level world) { return world != null && isTrainingServer(world.getServer()); }
    /** 所有 Carpet 假玩家都不占真人容量，包括职业派生的假玩家。 */
    public static boolean isManagedBot(ServerPlayer player) {
        return player != null && FabricLoader.getInstance().isModLoaded("carpet") && player instanceof carpet.patches.EntityPlayerMPFake;
    }
    public static boolean isAlive(ServerPlayer player) { return player != null && !player.isSpectator() && !player.isDeadOrDying() && !player.isRemoved(); }
    public static TrainingSession session(MinecraftServer server) { return isTrainingServer(server) && !dedicated ? session : null; }
    public static TrainingSession session(ServerPlayer player) {
        if (player == null || !isTrainingServer(player.getServer())) return null;
        if (!dedicated) return session;
        Camp own = camps.get(player.getUUID());
        if (own != null && own.player.connection == player.connection) return own.session;
        for (Camp camp : camps.values()) if (camp.session != null && (camp.session.owns(player)
                || isManagedBot(player) && player.serverLevel() == camp.session.level())) return camp.session;
        return null;
    }
    public static boolean canInteract(ServerPlayer actor, Entity target) {
        if (!isTrainingServer(actor.getServer())) return true;
        TrainingSession own = session(actor);
        if (own == null || actor.serverLevel() != own.level() || target.level() != own.level()) return false;
        return !(target instanceof ServerPlayer player) || session(player) == own;
    }
    public static boolean suppressFriendlyPenalty(ServerPlayer player) {
        TrainingSession own = session(player);
        return isTrainingServer(player.getServer()) && (own == null || !own.settings().friendlyFirePenalty);
    }
    public static void handle(ServerPlayer actor, TrainingActionPayload payload) {
        var server = actor.getServer();
        TrainingSession own = session(actor);
        if (own == null) return;
        Camp camp = camps.get(actor.getUUID());
        boolean allowed = dedicated
                ? TrainingAccessPolicy.permitsDedicated(isDedicatedTrainingServer(server), camp != null && pool.owns(actor.getUUID(), camp.lease.slot()),
                        camp != null && camp.player.connection == actor.connection, actor.serverLevel() == own.level())
                : TrainingAccessPolicy.permits(isTrainingServer(server), server.isDedicatedServer(), server.isPublished(),
                        server.isSingleplayerOwner(actor.getGameProfile()), actor.serverLevel() == server.overworld());
        if (!allowed) return;
        try { own.handle(actor, payload); }
        catch (RuntimeException e) {
            SRE.LOGGER.error("训练操作失败: {}", payload.action(), e);
            own.error("training.sre.error.action");
        }
    }
    private static final class Camp {
        final TrainingCampPool.Lease lease;
        ServerPlayer player;
        TrainingSession session;
        Camp(TrainingCampPool.Lease lease, ServerPlayer player) { this.lease = lease; this.player = player; }
    }
}
