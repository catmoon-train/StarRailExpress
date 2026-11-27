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

package io.wifi.starrailexpress.game.voting;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.vote.RolePoolVote;
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

    /**
     * 入口：开始「发车前」的投票流程。
     *
     * <p>
     * 默认先开一步<b>职业池投票</b>（{@link RolePoolVote}，由
     * {@link SREConfig#enableRolePoolVote} 控制）：池子确定后写进职业轮换名单，再自动
     * 接着开地图投票；池子投票没能开起来（例如已有其它投票在跑）时直接进入地图投票，
     * 保证原有流程不会因为新增阶段而卡住。
     *
     * @param votingTimeSeconds 地图投票时长（秒）；职业池阶段复用同一时长
     */
    public void startVoting(int votingTimeSeconds) {
        if (!canStartVoting()) {
            return;
        }
        if (RolePoolVote.isRunning()) {
            SRE.LOGGER.warn("Voting start failed: role pool vote is already running!");
            return;
        }
        if (SREConfig.instance().enableRolePoolVote
                && RolePoolVote.start(votingTimeSeconds, () -> beginMapVoting(votingTimeSeconds))) {
            return;
        }
        beginMapVoting(votingTimeSeconds);
    }

    /** 跳过职业池阶段，直接开地图投票（给 {@code /tmm:votemap nopool} 用）。 */
    public void startVotingDirect(int votingTimeSeconds) {
        if (!canStartVoting()) {
            return;
        }
        beginMapVoting(votingTimeSeconds);
    }

    private boolean canStartVoting() {
        if (GameUtils.isStartingGame) {
            SRE.LOGGER.warn("Voting start failed: Game is starting!");
            return false;
        }
        MinecraftServer server = SRE.SERVER;
        if (server == null) {
            return false;
        }
        Level level = server.overworld();
        if (SREGameWorldComponent.KEY.get(level).isRunning()) {
            SRE.LOGGER.warn("Voting start failed: Game has already started!");
            return false;
        }
        if (isVotingActive()) {
            SRE.LOGGER.warn("Voting start failed: map voting is already running!");
            return false;
        }
        return true;
    }

    /** 地图投票本体：广播候选地图并开始倒计时。 */
    private void beginMapVoting(int votingTimeSeconds) {
        MinecraftServer server = SRE.SERVER;
        if (server == null) {
            return;
        }
        Level level = server.overworld();
        votingMapconfigs = new MapConfig();
        votingMapconfigs.maps = ServerMapConfig.getInstance(server).getRandomMaps();
        MapVotingComponent votingComponent = MapVotingComponent.KEY.get(level);
        server.getPlayerList().getPlayers().forEach(
                serverPlayer -> {
                    ServerPlayNetworking.send(serverPlayer,
                            ShowSelectedMapUIPayload.ofCandidates(votingMapconfigs.maps));
                });
        votingComponent.startVoting(votingTimeSeconds);
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
        return SREGameModes.GAME_MODES.keySet().stream()
                .filter(a -> !a.equals(io.wifi.starrailexpress.training.TrainingGameMode.ID))
                .anyMatch(a -> a.getPath().equals(gameMode));
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