package org.agmas.noellesroles.game.modes.echo.visual;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 独立于私有 RoleData 的公共外观同步；完整快照也覆盖新观察者、重连、离场与换局。 */
public final class EchoVisualService {
    private record Sent(EchoVisualPayload payload, int tick) { }
    private static final Map<ServerPlayer, Sent> SENT = new IdentityHashMap<>();
    private static final int TREMBLE_LEASE_TICKS = 30;
    /** 只撤销本光环实际施加的实例，不清除 Dream 或指令施加的颤抖。 */
    private static final Map<ServerPlayer, MobEffectInstance> TREMBLING = new IdentityHashMap<>();
    private EchoVisualService() { }

    /** 在 EchoSessions.endTick 之后调用，读取最终生命状态，不参与战斗结算。 */
    public static void tick(MinecraftServer server) {
        for (var player : List.copyOf(TREMBLING.keySet()))
            if (server.getPlayerList().getPlayer(player.getUUID()) != player) updateTremble(player, false);
        SENT.keySet().removeIf(player -> server.getPlayerList().getPlayer(player.getUUID()) != player);
        for (var level : server.getAllLevels()) {
            var game = SREGameWorldComponent.KEY.get(level);
            var match = EchoSessions.get(level);
            boolean active = game.getGameMode() instanceof EchoGameMode
                    && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                    && match != null && match.result() == null;
            boolean actionsRunning = active && match.phase().acceptsActions()
                    && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(level).isTimeFrozen();
            var hunters = active ? match.onlinePlayers().stream().filter(player -> match.present(player)
                    && match.life(player.getUUID()).standing() && match.faction(player.getUUID()) == EchoFaction.INTERCEPTOR).toList()
                    : List.<ServerPlayer>of();
            var appearances = hunters.stream().map(ServerPlayer::getUUID).sorted().toList();
            // 恐惧半径作用于附近所有非杀手，不要求先被锁定；仅同步强度，不泄漏方向或坐标。
            Map<ServerPlayer, Float> pressure = new IdentityHashMap<>();
            if (actionsRunning) for (var receiver : match.onlinePlayers()) {
                if (!match.present(receiver) || !match.life(receiver.getUUID()).standing()
                        || match.faction(receiver.getUUID()) == EchoFaction.INTERCEPTOR) continue;
                for (var hunter : hunters) pressure.merge(receiver,
                        EchoVisualState.pressure(hunter.distanceTo(receiver), org.agmas.noellesroles.game.modes.echo.combat.EchoCombatService.PASSIVE_RANGE), Math::max);
            }
            for (var receiver : level.players()) {
                updateTremble(receiver, EchoVisualState.trembleEligible(active ? match.faction(receiver.getUUID()) : null,
                        active && match.present(receiver) && match.life(receiver.getUUID()).standing(), actionsRunning,
                        pressure.getOrDefault(receiver, 0F)));
                if (!ServerPlayNetworking.canSend(receiver, EchoVisualPayload.ID)) continue;
                var payload = new EchoVisualPayload(active ? match.id() : EchoVisualPayload.NONE,
                        level.dimension().location(), appearances, Math.round(100F * pressure.getOrDefault(receiver, 0F)));
                var previous = SENT.get(receiver);
                if (previous == null || !previous.payload().equals(payload) || server.getTickCount() - previous.tick() >= 10) {
                    // 非回声世界不反复发送空快照；转世界时 dimension 改变仍会立即撤销。
                    if (active || previous != null && !previous.payload().equals(payload))
                        ServerPlayNetworking.send(receiver, payload);
                    SENT.put(receiver, new Sent(payload, server.getTickCount()));
                }
            }
        }
    }


    private static void updateTremble(ServerPlayer player, boolean nearby) {
        var current = player.getEffect(ModEffects.TREMBLE);
        var owned = TREMBLING.get(player);
        if (owned != null && (current != owned || current.getAmplifier() != 0
                || current.getDuration() > TREMBLE_LEASE_TICKS)) {
            TREMBLING.remove(player); owned = null;
        }
        if (!nearby) {
            if (owned != null) player.removeEffect(ModEffects.TREMBLE);
            TREMBLING.remove(player);
        } else if (current == null) {
            var effect = new MobEffectInstance(ModEffects.TREMBLE, TREMBLE_LEASE_TICKS, 0, false, false, true);
            if (player.addEffect(effect) && player.getEffect(ModEffects.TREMBLE) == effect) TREMBLING.put(player, effect);
        } else if (owned != null && current.getDuration() <= 10) {
            player.addEffect(new MobEffectInstance(ModEffects.TREMBLE, TREMBLE_LEASE_TICKS, 0, false, false, true));
        }
    }

    public static void stop() {
        for (var player : List.copyOf(TREMBLING.keySet())) updateTremble(player, false);
        TREMBLING.clear(); SENT.clear();
    }
}
