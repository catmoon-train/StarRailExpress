package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.network.MapVotingResultsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MapVotingComponent implements AutoSyncedComponent, CommonTickingComponent {
    public static final ComponentKey<MapVotingComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("map_voting"),
            MapVotingComponent.class);

    private final Level world;
    private boolean votingActive = false;
    private boolean votingPaused = false;
    private boolean votingSystemPaused = false;
    private int votingTimeLeft = 0;
    private int totalVotingTime = 0;
    private String presetGameMode = "murder"; // 预设游戏模式，默认murder
    private final Map<String, Integer> votes = new HashMap<>();
    private final Map<UUID, String> playerVotes = new HashMap<>(); // 记录每个玩家的投票
    private boolean shouldSync = false;

    public MapVotingComponent(Level world) {
        this.world = world;
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        this.votingActive = tag.getBoolean("VotingActive");
        this.votingPaused = tag.getBoolean("VotingPaused");
        this.votingSystemPaused = tag.getBoolean("VotingSystemPaused");
        this.votingTimeLeft = tag.getInt("VotingTimeLeft");
        this.totalVotingTime = tag.getInt("TotalVotingTime");
        this.presetGameMode = tag.getString("PresetGameMode");
        if (this.presetGameMode.isEmpty()) {
            this.presetGameMode = "murder"; // 默认值
        }

        // 读取投票数据
        this.votes.clear();
        ListTag votesList = tag.getList("Votes", Tag.TAG_COMPOUND);
        for (int i = 0; i < votesList.size(); i++) {
            CompoundTag voteTag = votesList.getCompound(i);
            String mapId = voteTag.getString("MapId");
            int count = voteTag.getInt("Count");
            this.votes.put(mapId, count);
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("VotingActive", this.votingActive);
        tag.putBoolean("VotingPaused", this.votingPaused);
        tag.putBoolean("VotingSystemPaused", this.votingSystemPaused);
        tag.putInt("VotingTimeLeft", this.votingTimeLeft);
        tag.putInt("TotalVotingTime", this.totalVotingTime);
        tag.putString("PresetGameMode", this.presetGameMode);

        // 写入投票数据
        ListTag votesList = new ListTag();
        for (Map.Entry<String, Integer> entry : this.votes.entrySet()) {
            CompoundTag voteTag = new CompoundTag();
            voteTag.putString("MapId", entry.getKey());
            voteTag.putInt("Count", entry.getValue());
            votesList.add(voteTag);
        }
        tag.put("Votes", votesList);
    }

    @Override
    public void tick() {
        // 检查是否需要同步投票状态
        if (shouldSync) {
            sync();
            shouldSync = false;
        }

        // 处理投票倒计时
        if (world != null && world.isClientSide && votingActive && !votingSystemPaused && !votingPaused) {
            votingTimeLeft--;
        } else if (world != null && votingActive && !votingSystemPaused && !votingPaused) {
            votingTimeLeft--;
            if (votingTimeLeft <= 0) {
                finishVoting();
                sync();
            } else {
                // 每5秒同步一次倒计时
                if (votingTimeLeft % 100 == 0) {
                    shouldSync = true;
                }
            }
        }
    }

    @Overwrite
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        // return this.world != serverPlayer.level();
        return true;
    }

    public void sync() {
        if (world != null) {
            KEY.sync(world);
        }
    }

    // Getters
    public boolean isVotingActive() {
        return votingActive;
    }

    public boolean isVotingPaused() {
        return votingPaused;
    }

    public int getVotingTimeLeft() {
        return votingTimeLeft;
    }

    public int getTotalVotingTime() {
        return totalVotingTime;
    }

    public String getPresetGameMode() {
        return presetGameMode;
    }

    public int getVoteCount(String mapId) {
        return votes.getOrDefault(mapId, 0);
    }

    public Map<String, Integer> getAllVotes() {
        return new HashMap<>(votes);
    }

    // Setters with sync capability
    public void setVotingActive(boolean active) {
        this.votingActive = active;
        this.shouldSync = true;
    }

    public void setVotingPaused(boolean paused) {
        this.votingPaused = paused;
        this.shouldSync = true;
    }

    public void setVotingTimeLeft(int timeLeft) {
        this.votingTimeLeft = timeLeft;
        this.shouldSync = true;
    }

    public void setTotalVotingTime(int totalTime) {
        this.totalVotingTime = totalTime;
        this.shouldSync = true;
    }

    public boolean isVotingSystemPaused() {
        return votingSystemPaused;
    }

    public void setVotingSystemPaused(boolean paused) {
        this.votingSystemPaused = paused;
        this.shouldSync = true;
    }

    public void setPresetGameMode(String presetGameMode) {
        this.presetGameMode = presetGameMode;
        this.shouldSync = true;
    }

    // 投票管理方法
    public boolean voteForMap(UUID playerId, String mapId) {
        if (!votingActive || votingPaused) {
            return false;
        }

        // 如果玩家之前投过票，撤销之前的投票
        String previousVote = playerVotes.get(playerId);
        if (previousVote != null) {
            int currentCount = votes.getOrDefault(previousVote, 0);
            if (currentCount > 0) {
                votes.put(previousVote, currentCount - 1);
            }
        }

        // 记录新投票
        playerVotes.put(playerId, mapId);
        votes.put(mapId, votes.getOrDefault(mapId, 0) + 1);
        this.shouldSync = true;

        return true;
    }

    public String getMostVotedMap() {
        if (votes.isEmpty()) {
            return "random"; // 默认返回随机地图
        }

        int maxVotes = 0;
        String topMap = "random";
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                topMap = entry.getKey();
            } else if (entry.getValue() == maxVotes) {
                // 如果有多个相同票数的地图，随机选择
                if (Math.random() > 0.5) {
                    topMap = entry.getKey();
                }
            }
        }

        return topMap;
    }

    public void startVoting(int votingTimeSeconds) {
        reset();
        this.votingActive = true;
        this.votingPaused = false;
        this.votingTimeLeft = votingTimeSeconds;
        this.totalVotingTime = votingTimeSeconds;
        this.shouldSync = true;
    }

    public void pauseVoting() {
        if (votingActive && !votingPaused) {
            this.votingPaused = true;
            this.shouldSync = true;
        }
    }

    public void resumeVoting() {
        if (votingActive && votingPaused) {
            this.votingPaused = false;
            this.shouldSync = true;
        }
    }

    public void stopVoting() {
        this.votingActive = false;
        this.votingPaused = false;
        this.votingTimeLeft = 0;
        this.shouldSync = true;
    }

    private void finishVoting() {

        votingActive = false;

        // 获取得票最多地图
        String winningMap = getMostVotedMap();

        // 在服务器上执行函数
        MinecraftServer server = SRE.SERVER;
        if (SREGameWorldComponent.KEY.get(world).isRunning()) {
            // 已经启动了不要启动。
            return;
        }
        if (server != null) {

            if (!winningMap.equals("random")) {
                // 加载对应地图
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "tmm:switchmap load " + winningMap);
            } else {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "tmm:switchmap random");
            }
            // 执行投票结束函数
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "function harpymodloader:vote_over");

            // 开始游戏
            try {
                String resultGameMode = SREGameModes.MURDER.identifier.toString();
                // 根据预设游戏模式启动游戏
                for (ResourceLocation key : SREGameModes.GAME_MODES.keySet()) {
                    if (key.getPath().equals(presetGameMode)) {
                        resultGameMode = key.toString();
                    }
                }
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "tmm:start " + resultGameMode);
            } catch (Exception e) {
                SRE.LOGGER.error("投票结束后自动开始游戏失败", e);
            }
        }

        // 发送投票结果给所有玩家
        MapVotingResultsPayload payload = new MapVotingResultsPayload(winningMap);
        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }

        this.shouldSync = true;
    }

    public void reset() {
        this.votingActive = false;
        this.votingTimeLeft = 0;
        this.totalVotingTime = 0;
        this.votes.clear();
        this.playerVotes.clear();
        this.shouldSync = true;
    }
}