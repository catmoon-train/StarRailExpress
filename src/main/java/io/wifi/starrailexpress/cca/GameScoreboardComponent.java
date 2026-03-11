package io.wifi.starrailexpress.cca;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

import io.wifi.starrailexpress.SRE;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameScoreboardComponent implements CommonTickingComponent {
    public static final ComponentKey<GameScoreboardComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("gamescoreboard"), GameScoreboardComponent.class);
    private final Scoreboard scoreboard;
    private final MinecraftServer server;

    // 客观计分板
    private Objective gameTimerObjective;      // 游戏正计时
    private Objective gameTimeLeftObjective;   // 游戏倒计时
    private Objective playerTaskCountObjective; // 单个玩家完成的任务数量
    private Objective totalTaskCountObjective;  // 平民获胜所需完成的总任务数

    // 存储每个玩家完成的任务数
    private final Map<UUID, Integer> playerCompletedTasks = new HashMap<>();
    private int totalRequiredTasks = 0; // 平民获胜所需的总任务数

    public GameScoreboardComponent(Scoreboard scoreboard, MinecraftServer server) {
        this.scoreboard = scoreboard;
        this.server = server;
    }

    public void initializeObjectives() {
        // 创建游戏正计时目标 (游戏开始后的正计时)
        try {
            this.gameTimerObjective = this.scoreboard.getObjective("sre.gameTimer");
            if (this.gameTimerObjective == null) {
                // Logger.log
                this.gameTimerObjective = this.scoreboard.addObjective("sre.gameTimer", ObjectiveCriteria.DUMMY, 
                    net.minecraft.network.chat.Component.literal("Game Timer"), ObjectiveCriteria.RenderType.INTEGER,
                            true,
                        StyledFormat.NO_STYLE
                )
                ;
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("Failed to create game timer objective: {}", e.getMessage());
        }

        // 创建游戏倒计时目标
        try {
            this.gameTimeLeftObjective = this.scoreboard.getObjective("sre.gameTimeLeft");
            if (this.gameTimeLeftObjective == null) {
                this.gameTimeLeftObjective = this.scoreboard.addObjective("sre.gameTimeLeft", ObjectiveCriteria.DUMMY, 
                    net.minecraft.network.chat.Component.literal("Time Left"), ObjectiveCriteria.RenderType.INTEGER,
                        true,
                        StyledFormat.NO_STYLE);
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("Failed to create game time left objective: {}", e.getMessage());
        }

        // 创建玩家任务计数目标
        try {
            this.playerTaskCountObjective = this.scoreboard.getObjective("sre.playerTaskCount");
            if (this.playerTaskCountObjective == null) {
                this.playerTaskCountObjective = this.scoreboard.addObjective("sre.playerTaskCount", ObjectiveCriteria.DUMMY, 
                    net.minecraft.network.chat.Component.literal("Player Tasks"), ObjectiveCriteria.RenderType.INTEGER,
                        true,
                        StyledFormat.NO_STYLE);
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("Failed to create player task count objective: {}", e.getMessage());
        }

        // 创建总任务数目标
        try {
            this.totalTaskCountObjective = this.scoreboard.getObjective("sre.totalTaskCount");
            if (this.totalTaskCountObjective == null) {
                this.totalTaskCountObjective = this.scoreboard.addObjective("sre.totalTaskCount", ObjectiveCriteria.DUMMY, 
                    net.minecraft.network.chat.Component.literal("Total Tasks"), ObjectiveCriteria.RenderType.INTEGER,
                        true,
                        StyledFormat.NO_STYLE);
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("Failed to create total task count objective: {}", e.getMessage());
        }
    }

    @Override
    public void tick() {
        // 在tick方法中，我们需要获取world来访问游戏时间
        // 但我们没有直接访问world的方法，所以我们需要在其他地方更新计分板
        // 这个组件主要负责存储和提供计分板更新方法
    }

    public void updateGameTimers(net.minecraft.world.level.Level world) {
        if (server == null) return;

        // 更新游戏计时器
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        GameTimeComponent timeComponent = GameTimeComponent.KEY.get(world);

        if (gameComponent.isRunning() && timeComponent != null) {
            // 更新倒计时 (剩余时间)
            if (this.gameTimeLeftObjective != null) {
                int timeLeft = timeComponent.getTime();
                // 将时间转换为秒
                int secondsLeft = timeLeft / 20; // 假设游戏以20tps运行
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    var score = this.scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), this.gameTimeLeftObjective);
                    score.set(secondsLeft);
                }
            }

            // 更新正计时 (已用时间)
            if (this.gameTimerObjective != null) {
                // 计算已用时间 = 初始时间 - 剩余时间
                int timeUsed = timeComponent.getResetTime() - timeComponent.getTime();
                int secondsUsed = Math.max(0, timeUsed / 20);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    var score = this.scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), this.gameTimerObjective);
                    score.set(secondsUsed);
                }
            }
        }
    }

    public void updatePlayerTaskCount(Player player, int taskCount) {
        if (this.playerTaskCountObjective != null) {
            UUID playerUuid = player.getUUID();
            this.playerCompletedTasks.put(playerUuid, taskCount);
            
            var score = this.scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), this.playerTaskCountObjective);
            score.set(taskCount);
        }
    }

    public void incrementPlayerTaskCount(Player player) {
        int currentCount = this.playerCompletedTasks.getOrDefault(player.getUUID(), 0);
        currentCount++;
        updatePlayerTaskCount(player, currentCount);
    }

    public void setTotalRequiredTasks(int totalTasks) {
        this.totalRequiredTasks = totalTasks;
        
        if (this.totalTaskCountObjective != null) {
            // 更新所有玩家的总任务数显示
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                var score = this.scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), this.totalTaskCountObjective);
                score.set(totalTasks);
            }
        }
    }

    public void updateAllPlayerScores() {
        // 更新所有玩家的计分板显示
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updatePlayerTaskCount(player, this.playerCompletedTasks.getOrDefault(player.getUUID(), 0));
        }
    }

    public void reset() {
        this.playerCompletedTasks.clear();
        this.totalRequiredTasks = 0;
        initializeObjectives();
        
        // 重置所有计分板分数
        if (this.playerTaskCountObjective != null) {
            for (var playerName : this.scoreboard.getTrackedPlayers()) {
                this.scoreboard.resetSinglePlayerScore(playerName, this.playerTaskCountObjective);
            }
        }
        
        if (this.totalTaskCountObjective != null) {
            for (var playerName : this.scoreboard.getTrackedPlayers()) {
                this.scoreboard.resetSinglePlayerScore(playerName, this.totalTaskCountObjective);
            }
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // 从NBT读取数据
        this.totalRequiredTasks = tag.getInt("totalRequiredTasks");
        
        // 读取玩家任务计数
        this.playerCompletedTasks.clear();
        CompoundTag tasksTag = tag.getCompound("playerTasks");
        for (String key : tasksTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                int count = tasksTag.getInt(key);
                this.playerCompletedTasks.put(uuid, count);
            } catch (IllegalArgumentException e) {
                SRE.LOGGER.warn("Invalid UUID in NBT: {}", key);
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        // 写入NBT数据
        tag.putInt("totalRequiredTasks", this.totalRequiredTasks);
        
        // 保存玩家任务计数
        CompoundTag tasksTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : this.playerCompletedTasks.entrySet()) {
            tasksTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("playerTasks", tasksTag);
    }
}