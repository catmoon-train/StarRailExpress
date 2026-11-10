package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.GameplayExtensionEvents;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.init.ModEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/** 统一注册死亡、附身、复活及退出清理；仅本次纵火时刻的新死者可以接力恢复。 */
public final class ArsonistEvents {
    private static final Map<UUID, ServerLevel> PENDING_REVIVALS = new HashMap<>();
    private ArsonistEvents() {}

    public static void init() {
        ArsonistPackets.register();
        ArsonistIntroManager.register();
        ArsonistBurnedBodies.register();
        GameplayExtensionEvents.QUEST_REWARD.register((player, quest, streak, parallel, reward) ->
                SREGameWorldComponent.KEY.get(player.level()).isRole(player, SERoles.ARSONIST)
                        ? ArsonistRules.TASK_COINS : reward);
        OnPlayerDeathWithKiller.FINAL_EVENT.register((victim, killer, reason) -> {
            if (!(victim instanceof ServerPlayer target)) return;
            if (reason.equals(io.wifi.starrailexpress.game.GameConstants.DeathReasons.IGNITED)
                    && killer instanceof ServerPlayer arsonist
                    && SREGameWorldComponent.KEY.get(arsonist.level()).isRole(arsonist, SERoles.ARSONIST)) {
                for (ServerPlayer observer : target.server.getPlayerList().getPlayers()) {
                    observer.playNotifySound(net.minecraft.sounds.SoundEvents.BLAZE_DEATH,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.05F);
                }
            }
            var own = ArsonistRoleData.of(target);
            if (own != null) own.reset();
            for (var p : target.server.getPlayerList().getPlayers()) {
                var data = ArsonistRoleData.of(p);
                if (data != null && data.burnValues.containsKey(target.getUUID())) data.forgetTarget(target);
                if (data != null) data.onMomentDeath(target, reason);
            }
        });
        OnGameEnd.EVENT.register((level, game) -> {
            PENDING_REVIVALS.entrySet().removeIf(entry -> entry.getValue() == level);
            for (var p : level.getServer().getPlayerList().getPlayers()) {
                var data = ArsonistRoleData.of(p);
                if (data != null) data.reset(false);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_REVIVALS.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (var p : server.getPlayerList().getPlayers()) {
                var data = ArsonistRoleData.of(p);
                if (data != null && data.arsonMoment) data.maintainMomentSpectators();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var data = ArsonistRoleData.of(handler.player);
            if (data != null) data.reset();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerLevel pending = PENDING_REVIVALS.remove(handler.player.getUUID());
            if (pending != null && SREGameWorldComponent.KEY.get(pending).getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                    && !GameUtils.isPlayerAliveAndSurvival(handler.player)) {
                if (handler.player.serverLevel() != pending) {
                    var spawn = pending.getSharedSpawnPos();
                    handler.player.teleportTo(pending, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                            handler.player.getYRot(), handler.player.getXRot());
                }
                reviveMomentVictim(handler.player);
            }
            int heat = 0;
            ArsonistIntroManager.join(handler.player);
            for (var p : server.getPlayerList().getPlayers()) {
                var data = ArsonistRoleData.of(p);
                if (data != null) {
                    heat = Math.max(heat, data.burnValues.getOrDefault(handler.player.getUUID(), 0));
                    if (data.arsonMoment) ServerPlayNetworking.send(handler.player,
                            new ArsonistPackets.MomentS2C(p.getUUID(), true, data.momentTicks));
                    data.maintainMomentSpectators();
                }
            }
            ServerPlayNetworking.send(handler.player, new ArsonistPackets.HeatS2C(heat));
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var data = ArsonistRoleData.of(player);
            if (data == null || !data.arsonMoment) return InteractionResult.PASS;
            if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().is(SEItems.FLAMETHROWER)) {
                SEItems.FLAMETHROWER.use(level, player, hand);
            }
            return InteractionResult.CONSUME;
        });
    }

    /** 先消耗名单再调用本方法；离线死者只在当前对局重连时补恢复。 */
    static void releaseMomentVictims(ServerPlayer owner, Set<UUID> victims, boolean revive) {
        for (UUID id : victims) {
            ServerPlayer target = owner.server.getPlayerList().getPlayer(id);
            if (target == null || target.hasDisconnected()) {
                if (revive) PENDING_REVIVALS.put(id, owner.serverLevel());
                continue;
            }
            var penalty = DeathPenaltyComponent.KEY.get(target);
            if (owner.getUUID().equals(penalty.limitCameraUUID)) {
                penalty.clear();
                for (var effect : java.util.List.of(ModEffects.MOVE_BANED, ModEffects.USED_BANED)) {
                    var current = target.getEffect(effect);
                    if (current != null && current.getAmplifier() == 0 && current.getDuration() <= 40) target.removeEffect(effect);
                }
            }
            if (revive && !GameUtils.isPlayerAliveAndSurvival(target)) reviveMomentVictim(target);
            ServerPlayNetworking.send(target, new ArsonistPackets.SpectatorS2C(owner.getUUID(), false));
        }
        if (!victims.isEmpty()) GameUtils.pendingReJudgeSpectatorsPenalty();
    }

    private static void reviveMomentVictim(ServerPlayer target) {
        var body = GameUtils.findPlayerBodyEntity(target);
        if (body != null) body.discard();
        target.removeEffect(pro.fazeclan.river.stupid_express.constants.SEEffects.BURNING);
        target.clearFire();
        GameUtils.revivePlayerToItsRoom(target);
        target.sendSystemMessage(Component.translatable("message.stupid_express.arsonist.revived"));
    }
}
