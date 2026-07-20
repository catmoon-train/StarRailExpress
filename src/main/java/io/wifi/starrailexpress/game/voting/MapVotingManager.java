package io.wifi.starrailexpress.game.voting;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import io.wifi.starrailexpress.network.ShowSelectedMapUIPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

public class MapVotingManager {
    private static MapVotingManager instance;
    private MapConfig votingMapconfigs = null;

    public MapConfig getMapVotingCache() {
        return votingMapconfigs;
    }

    private MapVotingManager() {
    }

    public static synchronized MapVotingManager getInstance() {
        if (instance == null) {
            instance = new MapVotingManager();
        }
        return instance;
    }

    public void startVoting(int votingTimeSeconds) {
        if (GameUtils.isStartingGame) {
            SRE.LOGGER.warn("Voting start failed: Game is starting!");
            return;
        }
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level);
            if (gameWorldComponent.isRunning()) {
                SRE.LOGGER.warn("Voting start failed: Game has already started!");
                return;
            }
            votingMapconfigs = ShowSelectedMapUIPayload
                    .getRandomConfig(ServerMapConfig.getInstance(server));
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            server.getPlayerList().getPlayers().forEach(
                    serverPlayer -> {
                        ServerPlayNetworking.send(serverPlayer,
                                new ShowSelectedMapUIPayload(votingMapconfigs));
                    });
            votingComponent.startVoting(votingTimeSeconds);
        }
    }

    public void reset() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            votingComponent.reset();
        }
    }

    public void pauseVoting() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            votingComponent.pauseVoting();
        }
    }

    public void resumeVoting() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            votingComponent.resumeVoting();
        }
    }

    public void stopVoting() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            votingComponent.stopVoting();
        }
    }

    public boolean isVotingActive() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.isVotingActive();
        }
        return false;
    }

    public boolean isVotingPaused() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.isVotingPaused();
        }
        return false;
    }

    public boolean voteForMap(UUID playerId, String mapId) {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.voteForMap(playerId, mapId);
        }
        return false;
    }

    public String getMostVotedMap() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.getMostVotedMap();
        }

        return "random";
    }

    public int getVoteCount(String mapId) {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.getVoteCount(mapId);
        }
        return 0;
    }

    public Map<String, Integer> getAllVotes() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.getAllVotes();
        }
        return new java.util.HashMap<>();
    }

    public void tick() {
        // Tick 现在由 MapVotingComponent 处理
        // 但我们可以保留这个方法用于其他可能的逻辑
    }

    public int getVotingTimeLeft() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.getVotingTimeLeft();
        }
        return 0;
    }

    public int getTotalVotingTime() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.getTotalVotingTime();
        }
        return 0;
    }

    public boolean isValidGameMode(String gameMode) {
        return SREGameModes.GAME_MODES.keySet().stream().anyMatch(a -> a.getPath().equals(gameMode));
    }

    public void setPresetGameMode(String gameMode) {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            votingComponent.setPresetGameMode(gameMode);
        }
    }

    public boolean isVotingSystemPaused() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.isVotingSystemPaused();
        }
        return false;
    }

    public void setVotingSystemPaused(boolean paused) {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            votingComponent.setVotingSystemPaused(paused);
        }
    }
}