package org.agmas.noellesroles.game.modes.echo.map;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 每局仅投影一次固定设施；按接收者实例同步，所有阵营与旁观者收到相同快照。 */
public final class EchoFacilityMapService {
    public static final int RESEND_TICKS = 40;
    private record Sent(EchoFacilityMapPayload payload, int tick) { }
    private static final Map<ServerPlayer, Sent> SENT = new IdentityHashMap<>();
    private static final Map<EchoMatch, EchoFacilityMapPayload> SNAPSHOTS = new IdentityHashMap<>();

    private EchoFacilityMapService() { }

    /** 常量注册在 EchoSessions.endTick 后；换局、换维度和重连不等待保活周期。 */
    public static void tick(MinecraftServer server) {
        SENT.keySet().removeIf(player -> server.getPlayerList().getPlayer(player.getUUID()) != player);
        var activeMatches = Collections.newSetFromMap(new IdentityHashMap<EchoMatch, Boolean>());
        for (var level : server.getAllLevels()) {
            var game = SREGameWorldComponent.KEY.get(level);
            var match = EchoSessions.get(level);
            boolean active = game.getGameMode().identifier.equals(EchoBootstrap.ID)
                    && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                    && match != null && match.result() == null;
            var empty = new EchoFacilityMapPayload(EchoFacilityMapPayload.NONE, level.dimension().location(), "", List.of());
            var payload = empty;
            if (active) {
                activeMatches.add(match);
                // 包括异常结果也缓存，避免错误地图每 tick 重算、刷日志或影响对局结算。
                payload = SNAPSHOTS.computeIfAbsent(match, current -> {
                    try {
                        return new EchoFacilityMapPayload(current.id(), level.dimension().location(), current.map().mapId(),
                                EchoFacilityMapSnapshot.project(current.map(), current.playerCount()));
                    } catch (IllegalArgumentException failure) {
                        Noellesroles.LOGGER.error("回声设施地图无法生成完整快照（地图={}）", current.map().mapId(), failure);
                        return empty;
                    }
                });
            }
            for (var receiver : level.players()) {
                if (!ServerPlayNetworking.canSend(receiver, EchoFacilityMapPayload.ID)) {
                    SENT.remove(receiver);
                    continue;
                }
                var previous = SENT.get(receiver);
                boolean changed = previous == null || !previous.payload().match().equals(payload.match())
                        || !previous.payload().dimension().equals(payload.dimension());
                if (changed || active && server.getTickCount() - previous.tick() >= RESEND_TICKS) {
                    ServerPlayNetworking.send(receiver, payload);
                    SENT.put(receiver, new Sent(payload, server.getTickCount()));
                }
            }
        }
        SNAPSHOTS.keySet().retainAll(activeMatches);
    }

    public static void stop() {
        SENT.clear();
        SNAPSHOTS.clear();
    }
}
