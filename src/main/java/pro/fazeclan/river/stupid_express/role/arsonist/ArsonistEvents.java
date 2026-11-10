package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.GameplayExtensionEvents;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/** 统一注册一次事件；不再含任何附身或复活分支。 */
public final class ArsonistEvents {
    private ArsonistEvents() {}

    public static void init() {
        ArsonistPackets.register();
        GameplayExtensionEvents.QUEST_REWARD.register((player, quest, streak, parallel, reward) ->
                SREGameWorldComponent.KEY.get(player.level()).isRole(player, SERoles.ARSONIST)
                        ? ArsonistRules.TASK_COINS : reward);
        OnPlayerDeathWithKiller.FINAL_EVENT.register((victim, killer, reason) -> {
            if (!(victim instanceof ServerPlayer target)) return;
            if (reason.equals(io.wifi.starrailexpress.game.GameConstants.DeathReasons.IGNITED)
                    && killer instanceof ServerPlayer arsonist
                    && SREGameWorldComponent.KEY.get(arsonist.level()).isRole(arsonist, SERoles.ARSONIST)) {
                var message = net.minecraft.network.chat.Component.translatable(
                        "message.stupid_express.arsonist.burn_kill", arsonist.getDisplayName(), target.getDisplayName());
                for (ServerPlayer observer : target.server.getPlayerList().getPlayers()) {
                    org.agmas.noellesroles.commands.BroadcastCommand.BroadcastMessage(observer, message);
                    observer.sendSystemMessage(message);
                    observer.playNotifySound(net.minecraft.sounds.SoundEvents.BLAZE_DEATH,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.05F);
                }
            }
            var own = ArsonistRoleData.of(target);
            if (own != null) own.reset();
            for (var p : target.server.getPlayerList().getPlayers()) {
                var data = ArsonistRoleData.of(p);
                if (data != null && data.burnValues.containsKey(target.getUUID())) data.forgetTarget(target);
            }
        });
        OnGameEnd.EVENT.register((level, game) -> {
            for (var p : level.getServer().getPlayerList().getPlayers()) {
                var data = ArsonistRoleData.of(p);
                if (data != null) data.reset();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var data = ArsonistRoleData.of(handler.player);
            if (data != null) data.reset();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            int heat = 0;
            for (var p : server.getPlayerList().getPlayers()) {
                var data = ArsonistRoleData.of(p);
                if (data != null) {
                    heat = Math.max(heat, data.burnValues.getOrDefault(handler.player.getUUID(), 0));
                    if (data.arsonMoment) ServerPlayNetworking.send(handler.player,
                            new ArsonistPackets.MomentS2C(p.getUUID(), true, data.momentTicks));
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
}
