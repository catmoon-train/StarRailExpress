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

public class PlayerJoinUtils {
    private static final long WAITING_TIME = 100;

    public static record NewPlayerInfo(UUID player, long joinTime) {
    }

    private static final ConcurrentHashMap<UUID, Long> pendingJoins = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayerJoinUtils::tick);
        OnGameInitialized.EVENT.register((t) -> {
            pendingJoins.clear();
        });
    }

    public static void onPlayerJoin(ServerPlayer serverPlayer) {

        GameReplayManager.playerNames.put(serverPlayer.getUUID(), serverPlayer.getScoreboardName());

        PlayerJoinUtils.adjustPlayerPosition(serverPlayer);
        SyncMapConfigPayload.sendToPlayer(serverPlayer);
        SREGameWorldComponent.KEY.syncWith(serverPlayer, (ComponentProvider) serverPlayer.level());
        pendingJoins.put(serverPlayer.getUUID(), System.currentTimeMillis());
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
            if (serverPlayer.level() instanceof ServerLevel serverWorld) {
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
