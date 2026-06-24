package org.agmas.noellesroles.scene;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.agmas.noellesroles.content.entity.HurricaneEntity;
import org.agmas.noellesroles.init.ModEntities;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SceneRuntimeEvents {
    private static final Map<UUID, Integer> ZERO_AIR_TICKS = new HashMap<>();

    private SceneRuntimeEvents() {
    }

    public static void register() {
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            clearHurricanes(world);
            ZERO_AIR_TICKS.clear();
            MapStatusBarRuntime.clear(world);
        });
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level instanceof ServerLevel serverLevel) {
                tickOxygenDrowning(serverLevel);
                MapStatusBarRuntime.tick(serverLevel);
            }
        });
    }

    public static void clearHurricanes(ServerLevel level) {
        for (HurricaneEntity hurricane : level.getEntities(ModEntities.HURRICANE, entity -> true)) {
            hurricane.discard();
        }
    }

    private static void tickOxygenDrowning(ServerLevel level) {
        if (!SREGameWorldComponent.KEY.get(level).isRunning() || !isOxygenDrowningEnabled(level)) {
            clearOxygenDrowning(level);
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                ZERO_AIR_TICKS.remove(player.getUUID());
                continue;
            }
            if (player.getAirSupply() <= 0) {
                int ticks = ZERO_AIR_TICKS.getOrDefault(player.getUUID(), 0) + 1;
                ZERO_AIR_TICKS.put(player.getUUID(), ticks);
                if (ticks >= 5 * 20) {
                    ZERO_AIR_TICKS.remove(player.getUUID());
                    GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.DROWNED);
                }
            } else {
                ZERO_AIR_TICKS.remove(player.getUUID());
            }
        }
    }

    private static boolean isOxygenDrowningEnabled(ServerLevel level) {
        return AreasWorldComponent.KEY.get(level).enableOxygenDrowning;
    }

    private static void clearOxygenDrowning(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            ZERO_AIR_TICKS.remove(player.getUUID());
        }
    }
}
