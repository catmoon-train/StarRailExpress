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

package io.wifi.starrailexpress;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.ladysnake.cca.api.v3.component.ComponentProvider;

import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus;
import io.wifi.starrailexpress.event.OnGameInitialized;
import io.wifi.starrailexpress.network.SyncMapConfigPayload;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class PlayerJoinUtils {
    private static final long WAITING_TIME = 500;

    // 新增：5秒后位置检查的等待时间
    private static final long POSITION_CHECK_DELAY = 3000;

    public static record NewPlayerInfo(UUID player, long joinTime) {
    }

    private static final ConcurrentHashMap<UUID, Long> pendingJoins = new ConcurrentHashMap<>();
    // 新增：用于5秒后位置检查的记录
    private static final ConcurrentHashMap<UUID, Long> positionCheckPending = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayerJoinUtils::tick);
        OnGameInitialized.EVENT.register((t) -> {
            pendingJoins.clear();
            positionCheckPending.clear(); // 清空新map
        });
    }

    public static void onPlayerJoin(ServerPlayer serverPlayer) {

        GameReplayManager.playerNames.put(serverPlayer.getUUID(), serverPlayer.getScoreboardName());

        adjustPlayerPosition(serverPlayer);

        SyncMapConfigPayload.sendToPlayer(serverPlayer);
        SREGameWorldComponent.KEY.syncWith(serverPlayer, (ComponentProvider) serverPlayer.level());
        pendingJoins.put(serverPlayer.getUUID(), System.currentTimeMillis());
        positionCheckPending.put(serverPlayer.getUUID(), System.currentTimeMillis());
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = pendingJoins.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID uuid = entry.getKey();
            long joinTime = entry.getValue();
            if (now - joinTime >= WAITING_TIME) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    adjustPlayerPosition(player);
                }
                it.remove(); // 安全删除
            }
        }

        // 新增：5秒后位置合法性检查
        Iterator<Map.Entry<UUID, Long>> checkIt = positionCheckPending.entrySet().iterator();
        while (checkIt.hasNext()) {
            Map.Entry<UUID, Long> entry = checkIt.next();
            UUID uuid = entry.getKey();
            long joinTime = entry.getValue();
            if (now - joinTime >= POSITION_CHECK_DELAY) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    // 检查位置是否合法，不合法则传回出生点
                    if (!isPlayerPositionValid(player)) {
                        adjustPlayerPosition(player);
                    }
                }
                checkIt.remove();
            }
        }
    }

    /**
     * 判断玩家当前位置是否合法
     * 
     * @param player 待检查的玩家
     * @return
     */
    private static boolean isPlayerPositionValid(ServerPlayer player) {
        Vec3 playerPos = player.position();
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());

        // MapVotingComponent mapVotingComponent =
        // MapVotingComponent.KEY.get(serverPlayer.level());
        // if (mapVotingComponent.isVotingActive()){
        // if (TMMConfig.mapRandomCount!=-1){
        // ServerPlayNetworking.send(serverPlayer, new ShowSelectedMapUIPayload(true));
        // }
        // }
        if (gameWorldComponent.getGameStatus() == GameStatus.ACTIVE) {
            if (player.isSpectator())
                return true;
            return false;
        }
        final ServerLevel serverWorld = player.serverLevel();

        BlockPos spawn = serverWorld.getSharedSpawnPos();
        if (spawn.getX() - 200 < playerPos.x && playerPos.x < spawn.getX() + 200) {
            if (spawn.getZ() - 200 < playerPos.z && playerPos.z < spawn.getZ() + 200) {
                return true;
            }
        }
        return false;
    }

    public static void adjustPlayerPosition(ServerPlayer serverPlayer) {
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());

        // MapVotingComponent mapVotingComponent =
        // MapVotingComponent.KEY.get(serverPlayer.level());
        // if (mapVotingComponent.isVotingActive()){
        // if (TMMConfig.mapRandomCount!=-1){
        // ServerPlayNetworking.send(serverPlayer, new ShowSelectedMapUIPayload(true));
        // }
        // }
        if (gameWorldComponent.getGameStatus() == GameStatus.ACTIVE) {
            if (serverPlayer.level() instanceof ServerLevel serverWorld) {

                AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
                AreasWorldComponent.PosWithOrientation spectatorSpawnPos = areas.getSpectatorSpawnPos();
                serverPlayer.teleportTo(serverWorld, spectatorSpawnPos.pos.x(), spectatorSpawnPos.pos.y(),
                        spectatorSpawnPos.pos.z(), spectatorSpawnPos.yaw, spectatorSpawnPos.pitch);
                serverPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);

            }
        } else {
            if (serverPlayer.serverLevel() instanceof ServerLevel serverWorld) {
                BlockPos spawn = serverWorld.getSharedSpawnPos();
                float angle = serverWorld.getSharedSpawnAngle();
                serverPlayer.teleportTo(serverWorld, spawn.getX(), spawn.getY(),
                        spawn.getZ(), angle, 0);
                SREItemUtils.clearItem(serverPlayer, (a) -> true);
                if (!serverPlayer.isCreative())
                    serverPlayer.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            }

        }
    }
}
