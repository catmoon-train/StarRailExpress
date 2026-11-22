package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 训练仅在带身份标记的本地集成服中启用，场地固定使用主世界。 */
public final class TrainingRules {
    private static volatile MinecraftServer isolatedServer;
    private static TrainingSession session;
    private static boolean registered;
    private static boolean failed;
    private TrainingRules() { }


    public static void register() {
        if (registered) return;
        registered = true;
        SREGameModes.registerGameMode(new TrainingGameMode());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (!server.isDedicatedServer() && TrainingWorldInstaller.isTrainingWorld(server.getWorldPath(LevelResource.ROOT))) {
                isolatedServer = server; failed = false;
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!isTrainingServer(server)) return;
            var game = SREGameWorldComponent.KEY.get(server.overworld());
            game.setGameStatus(SREGameWorldComponent.GameStatus.INACTIVE);
            game.setGameMode(SREGameModes.GAME_MODES.get(TrainingGameMode.ID));
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
                !level.isClientSide && player instanceof ServerPlayer actor && !canInteract(actor, target)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) ->
                !(source.getEntity() instanceof ServerPlayer actor) || canInteract(actor, target));
        // Carpet 的原版 die 会断开连接；本会话的训练假人改走正常游戏死亡/旁观，等待原地复活。
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer bot) || !isTrainingServer(bot.getServer()) || !isManagedBot(bot)) return true;
            TrainingSession own = session(bot);
            if (own == null || !own.authorized() || !own.owns(bot) || bot.serverLevel() != own.level()) return true;
            boolean wasSpectator = bot.isSpectator();
            bot.setHealth(bot.getMaxHealth());
            if (!wasSpectator) GameUtils.killPlayer(bot, true,
                    source.getEntity() instanceof net.minecraft.world.entity.player.Player killer ? killer : null,
                    GameConstants.DeathReasons.GENERIC);
            return false;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!isTrainingServer(server)) return;
            // 开放到局域网不结束训练：场地仍只归集成服主人管理，客人不会收到训练状态。
            if (session == null && !failed && server.getTickCount() >= 20) {
                var owner = server.getPlayerList().getPlayers().stream().filter(p -> server.isSingleplayerOwner(p.getGameProfile())).findFirst().orElse(null);
                if (owner == null) return;
                if (!FabricLoader.getInstance().isModLoaded("carpet-dummy-player")) {
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
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (!isTrainingServer(server)) return;
            isolatedServer = null; session = null; failed = false;
        });
    }


    public static boolean isIsolated() { return isolatedServer != null; }
    public static boolean isTrainingServer(MinecraftServer server) { return server != null && server == isolatedServer; }
    public static boolean isTrainingWorld(Level world) { return world != null && isTrainingServer(world.getServer()); }
    /** 所有假玩家都不占真人容量，包括职业派生的假玩家。 */
    public static boolean isManagedBot(ServerPlayer player) {
        return player != null && FabricLoader.getInstance().isModLoaded("carpet-dummy-player")
                && player instanceof carpetdummy.patches.EntityPlayerMPFake;
    }

    public static boolean isControllingDummy(ServerPlayer player) {
        return player != null && FabricLoader.getInstance().isModLoaded("carpet-dummy-player")
                && carpetdummy.api.DummyPlayerApi.isControllingDummy(player);
    }
    public static boolean isAlive(ServerPlayer player) { return player != null && !player.isSpectator() && !player.isDeadOrDying() && !player.isRemoved(); }
    public static TrainingSession session(MinecraftServer server) { return isTrainingServer(server) ? session : null; }
    public static TrainingSession session(ServerPlayer player) {
        return player == null ? null : session(player.getServer());
    }

    /** 训练场假人的出生点房间即其初始点位；非本会话假人返回 null。 */
    public static @Nullable Vec3 botRoomSpawn(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return null;
        TrainingSession own = session(serverPlayer);
        return own != null && own.owns(serverPlayer) ? own.botSpawn(serverPlayer) : null;
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
        boolean allowed = TrainingAccessPolicy.permits(isTrainingServer(server), server.isDedicatedServer(),
                server.isSingleplayerOwner(actor.getGameProfile()), actor.serverLevel() == server.overworld());
        if (!allowed) return;
        try { own.handle(actor, payload); }
        catch (RuntimeException e) {
            SRE.LOGGER.error("训练操作失败: {}", payload.action(), e);
            own.error("training.sre.error.action");
        }
    }
}
