/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.content.entity.HurricaneEntity;
import org.agmas.noellesroles.init.ModEntities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SceneRuntimeEvents {
    private static final Map<UUID, Integer> ZERO_AIR_TICKS = new HashMap<>();

    private SceneRuntimeEvents() {
    }

    public static void register() {
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            clearHurricanes(world);
            ZERO_AIR_TICKS.clear();
            MapStatusBarRuntime.clear(world);
            SceneEventManager.clear(world);
            resetSabotageMinigameQuestCooldowns(world);
        });
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level instanceof ServerLevel serverLevel) {
                tickOxygenDrowning(serverLevel);
                MapStatusBarRuntime.tick(serverLevel);
                SceneEventManager.tickSabotageAlarm(serverLevel);
            }
        });
    }

    public static void clearHurricanes(ServerLevel level) {
        for (HurricaneEntity hurricane : level.getEntities(ModEntities.HURRICANE, entity -> true)) {
            hurricane.discard();
        }
    }

    private static void resetSabotageMinigameQuestCooldowns(ServerLevel level) {
        if (GameUtils.taskBlocks == null || GameUtils.taskBlocks.isEmpty()) {
            return;
        }
        for (var entry : GameUtils.taskBlocks.entrySet()) {
            int type = entry.getValue();
            if (type != 14 && type != 15) {
                continue;
            }
            if (level.getBlockEntity(entry.getKey()) instanceof MinigameQuestBlockEntity questBe
                    && questBe.isSabotageTrigger()
                    && questBe.getLastSabotageTime() != 0) {
                questBe.setLastSabotageTime(0);
            }
        }
    }

    private static void tickOxygenDrowning(ServerLevel level) {
        final boolean killerNoDrowing = isKillerNoDrowing(level);
        if (!SREGameWorldComponent.KEY.get(level).isRunning() || !isOxygenDrowningEnabled(level)) {
            clearOxygenDrowning(level);
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)
                    || (killerNoDrowing && SREGameWorldComponent.isKillerTeamStatic(player))) {
                ZERO_AIR_TICKS.remove(player.getUUID());
                continue;
            }
            if (player.getAirSupply() <= 0) {
                if ((killerNoDrowing && SREGameWorldComponent.isKillerTeamStatic(player))) {
                    continue;
                }
                int ticks = ZERO_AIR_TICKS.getOrDefault(player.getUUID(), 0) + 1;
                ZERO_AIR_TICKS.put(player.getUUID(), ticks);
                if (ticks >= 5 * 20) {
                    ZERO_AIR_TICKS.remove(player.getUUID());
                    player.setAirSupply(Player.TOTAL_AIR_SUPPLY);
                    GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.DROWNED);
                }
            } else {
                ZERO_AIR_TICKS.remove(player.getUUID());
            }
        }
    }

    private static boolean isKillerNoDrowing(ServerLevel level) {
        return AreasWorldComponent.KEY.get(level).areasSettings.killerNoDrowing;
    }

    private static boolean isOxygenDrowningEnabled(ServerLevel level) {
        return AreasWorldComponent.KEY.get(level).areasSettings.enableOxygenDrowning;
    }

    private static void clearOxygenDrowning(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            ZERO_AIR_TICKS.remove(player.getUUID());
        }
    }
}
