package io.wifi.starrailexpress.voting;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;

public class MapVotingManager {
    private static MapVotingManager instance;

    private MapVotingManager() {
    }

    public static synchronized MapVotingManager getInstance() {
        if (instance == null) {
            instance = new MapVotingManager();
        }
        return instance;
    }

    public void startVoting(int votingTimeSeconds) {
        if (GameFunctions.isStartingGame) {
            SRE.LOGGER.warn("Voting start failed: Game is starting!");
            return;
        }
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(level);
            if (gameWorldComponent.isRunning()) {
                SRE.LOGGER.warn("Voting start failed: Game has already started!");
                return;
            }

            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
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

    public boolean isVotingActive() {
        MinecraftServer server = SRE.SERVER;
        if (server != null) {
            Level level = server.overworld();
            MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
            return votingComponent.isVotingActive();
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
}