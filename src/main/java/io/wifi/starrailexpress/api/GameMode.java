package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowSpectatorPlayerInAreas;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public abstract class GameMode {
    public final ResourceLocation identifier;
    public final int defaultStartTime;
    public final int minPlayerCount;

    /**
     * @param identifier       游戏的id
     * @param defaultStartTime 默认游戏时长（分钟）
     * @param minPlayerCount   最小启动玩家人数
     */
    public GameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        this.identifier = identifier;
        this.defaultStartTime = defaultStartTime;
        this.minPlayerCount = minPlayerCount;
    }

    /**
     * 服务端/客户端共通主循环
     * 
     * @return
     */
    public void tickCommonGameLoop() {
    }

    /**
     * 客户端游戏主循环
     * 
     * @return
     */
    public void tickClientGameLoop() {
    }

    /**
     * 此模式是否必须有职业（true将导致没职业的玩家变旁观）
     * 
     * @return
     */
    public boolean requiresAssignedRole() {
        return true;
    }

    /**
     * 是否是亡命徒模式
     * 
     * @return
     */
    public boolean isLooseEndMode() {
        return false;
    }

    /**
     * 记录玩家数据
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void recordPlayerStats(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        GameUtils.recordPlayerStats(serverWorld, gameComponent, readyPlayerList);
    }

    /**
     * 在游戏开始initializeGame后触发，在OnGameTrueStarted前触发
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void gameTrueStarted(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
        if (hasSafeTime()) {
            GameUtils.addItemCooldowns(serverWorld, SAFE_TIME_COOLDOWN);
        }
        GameUtils.executeFunction(serverWorld.getServer().createCommandSourceStack(),
                "harpymodloader:start_game_" + AreasWorldComponent.KEY.get(serverWorld).mapName);
    }

    /**
     * 是否启用区域外检测（主要是y轴和水）
     * 
     * @return
     */
    public boolean enforcesPlayAreaElimination() {
        return true;
    }

    /**
     * 服务器游戏主循环
     * 
     * @param serverWorld
     * @param gameWorldComponent
     */

    public abstract void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent);

    /**
     * 初始化游戏，还未正式开始。
     * 
     * @param serverWorld
     * @param gameWorldComponent
     * @param players
     */
    public abstract void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players);

    /**
     * 在initializeGame前执行（baseInitialize）
     * 
     * @param serverWorld
     * @param gameWorldComponent
     * @param players
     */
    public void beforeInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        GameUtils.baseInitialize(serverWorld, gameWorldComponent, players);
    }

    /**
     * 游戏结束
     * 
     * @param serverWorld
     * @param gameWorldComponent
     */
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
    }

    public boolean hasSafeTime() {
        return true;
    }

    public boolean hasMood() {
        return true;
    }

    public Component getName() {
        // 根据游戏模式ID返回本地化的名称
        String gameModeId = this.identifier.getPath();
        return Component.translatableWithFallback("hud.sre.tip.gamemode." + gameModeId, gameModeId);
    }

    /**
     * 在initializeGame后执行
     * 
     * @param serverWorld
     * @param gameComponent
     * @param readyPlayerList
     */
    public void afterInitializeGame(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        // 初始化回放管理器,此时角色已经分配完成
        SRE.REPLAY_MANAGER.initializeReplay(readyPlayerList, gameComponent.getRoles());
        // 记录游戏开始事件
        SRE.REPLAY_MANAGER.addEvent(GameReplayData.EventType.GAME_START, null, null, null, null);

        // Update replay with actual roles after assignment
        SRE.REPLAY_MANAGER.updateRolesFromComponent(gameComponent);
    }

    /**
     * 触发游戏结束时触发（并未返回大厅）
     * 
     * @param world
     */
    public void stopGame(ServerLevel world) {
    }

    /**
     * 记录玩家胜利
     * 
     * @param world
     * @param roundEnd
     * @param gameComponent
     */
    public void recordWinStats(ServerLevel world, SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent gameComponent) {
        GameUtils.recordWinStats(world, roundEnd, gameComponent, this.isLooseEndMode());
    }

    /**
     * 游戏结束后显示replay调用
     * 
     * @param world
     * @param roundEnd
     * @param gameComponent
     */
    public void showReplay(ServerLevel world, SREGameRoundEndComponent roundEnd, SREGameWorldComponent gameComponent) {
        Component text = SRE.REPLAY_MANAGER.generateReplay();
        for (ServerPlayer player : world.players()) {
            GameReplayManager.sendSystemMessage(player, text);
        }
    }

    /**
     * 限制旁观者的游戏区域
     * 
     * @param player
     * @param gameWorldComponent
     * @param areas
     */
    public void limitSpectatorPlayer(ServerPlayer player, SREGameWorldComponent gameWorldComponent,
            AreasWorldComponent areas) {
        if (!AllowSpectatorPlayerInAreas.EVENT.invoker().allowInAreas(player)) {
            GameUtils.limitPlayerToBox(player, areas.getPlayArea());
        }
    }
}