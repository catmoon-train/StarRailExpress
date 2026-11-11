/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.training.TrainingRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 游戏工具的兼容门面：保留现有方法、字段及嵌套类型，具体实现按职责分离。
 * 公开可变字段只在这里存储一份，职责类不能复制或缓存这些容器引用。
 * 自定义获胜请使用 RoleUtils.customWinnerWin()，不要增加胜负枚举。
 */
public class GameUtils {
    public static HashMap<BlockPos, Integer> taskBlocks = new HashMap<>();
    public static ArrayList<BlockPos> resetPoints = new ArrayList<>();
    public static ArrayList<ServerTaskInfoClasses.ServerTaskInfo> serverTaskQueue = new ArrayList<>();
    public static ArrayList<ServerTaskInfoClasses.ServerTaskInfo> serverAsynTaskLists = new ArrayList<>();
    public static HashMap<UUID, PlayerKillInfo> serverCacheKillState = new HashMap<>();
    public static boolean isStartingGame = false;
    public static boolean isGameStarted = false;
    public static List<Item> cooldownItems = new ArrayList<>();
    public static long startTime = 0;
    public static Map<UUID, Integer> roomToPlayer = new HashMap<>();
    public static final Set<ChunkPos> chunksToClearEntities = new HashSet<>();
    /** 只在列车重置后的短窗口内清理，过期即整体作废。 */
    public static long chunksToClearEntitiesDeadline = 0;
    public static final float DEFAULT_FLYING_SPEED = 0.05F;
    public static final float DEFAULT_WALKING_SPEED = 0.1F;

    // 对局生命周期。
    public static void startGame(ServerLevel world, GameMode gameMode, int time) {
        if (io.wifi.starrailexpress.training.TrainingRules.isTrainingWorld(world)
                || gameMode instanceof io.wifi.starrailexpress.training.TrainingGameMode) return;
        GameLifecycle.startGame(world, gameMode, time);
    }

    public static void trueStartGame(ServerLevel world, GameMode gameMode, int time) {
        if (io.wifi.starrailexpress.training.TrainingRules.isTrainingWorld(world)
                || gameMode instanceof io.wifi.starrailexpress.training.TrainingGameMode) return;
        GameLifecycle.trueStartGame(world, gameMode, time);
    }

    public static void stopGame(ServerLevel world) {
        if (io.wifi.starrailexpress.training.TrainingRules.isTrainingWorld(world)) return;
        GameLifecycle.stopGame(world);
    }

    public static void initializeGame(ServerLevel world) {
        GameLifecycle.initializeGame(world);
    }

    public static void baseInitialize(ServerLevel world, SREGameWorldComponent component, List<ServerPlayer> players) {
        GameLifecycle.baseInitialize(world, component, players);
    }

    public static void finalizeGame(ServerLevel world) {
        if (io.wifi.starrailexpress.training.TrainingRules.isTrainingWorld(world)) return;
        GameLifecycle.finalizeGame(world);
    }

    public static void setForcedReadyPlayers(Collection<UUID> playerIds) {
        GameLifecycle.setForcedReadyPlayers(playerIds);
    }

    public static void clearForcedReadyPlayers() {
        GameLifecycle.clearForcedReadyPlayers();
    }

    public static int getStartingPlayerCount(ServerLevel world) {
        return GameLifecycle.getStartingPlayerCount(world);
    }

    public static void registerEventForServerTickForDoingResetTasks() {
        GameTaskScheduler.register();
    }

    // 玩家重置、状态与阵营。
    public static void resetPlayerAbilities(ServerPlayer player) {
        GamePlayerState.resetPlayerAbilities(player);
    }

    public static void normalizeSpectatorFlightAbilities(ServerPlayer player) {
        GamePlayerState.normalizeSpectatorFlightAbilities(player);
    }

    public static void releaseRoleFlight(ServerPlayer player) {
        GamePlayerState.releaseRoleFlight(player);
    }

    public static void resetPlayer(ServerPlayer player) {
        GamePlayerState.resetPlayer(player);
    }

    public static void resetPlayerAfterGame(ServerPlayer player) {
        GamePlayerState.resetPlayerAfterGame(player);
    }

    public static boolean differentTeam(SRERole role1, SRERole role2) {
        return GamePlayerState.differentTeam(role1, role2);
    }

    /** 按玩家实际阵营判断敌我；仅有职业定义时使用职业维度的重载。 */
    public static boolean differentTeam(Player player1, Player player2) {
        return GamePlayerState.differentTeam(player1, player2);
    }

    public static boolean isPlayerEliminatedIgnoreShitSplit(Player player) {
        return GamePlayerState.isPlayerEliminatedIgnoreShitSplit(player);
    }

    public static boolean isPlayerEliminated(Player player) {
        return GamePlayerState.isPlayerEliminated(player);
    }

    public static boolean isPlayerSpectator(Player player) {
        return GamePlayerState.isPlayerSpectator(player);
    }

    public static boolean isPlayerAliveAndSurvivalIgnoreShitSplit(Player player) {
        return GamePlayerState.isPlayerAliveAndSurvivalIgnoreShitSplit(player);
    }

    public static boolean isPlayerAliveAndSurvival(Player player, WorldModifierComponent modifiers) {
        return GamePlayerState.isPlayerAliveAndSurvival(player, modifiers);
    }

    public static boolean isPlayerAliveAndSurvival(Player player) {
        return GamePlayerState.isPlayerAliveAndSurvival(player);
    }

    public static boolean isPlayerCreative(Player player) {
        return GamePlayerState.isPlayerCreative(player);
    }

    public static boolean isPlayerSpectatingOrCreative(Player player) {
        return GamePlayerState.isPlayerSpectatingOrCreative(player);
    }

    public static SPAliveResult isPlayerReallyAliveOrDead(Player player) {
        return GamePlayerState.isPlayerReallyAliveOrDead(player);
    }

    public static SPAliveResult isPlayerReallyAliveOrDead(Player player, WorldModifierComponent modifiers) {
        return GamePlayerState.isPlayerReallyAliveOrDead(player, modifiers);
    }

    public static boolean isPlayerSpectatingOrCreativeIgnoreShitSplit(Player player) {
        return GamePlayerState.isPlayerSpectatingOrCreativeIgnoreShitSplit(player);
    }

    public static int getReadyPlayerCount(Level world) {
        return GamePlayerState.getReadyPlayerCount(world);
    }

    public static int getParticipatingPlayerCount(Level world) {
        return GamePlayerState.getParticipatingPlayerCount(world);
    }

    public static int getOptedOutPlayerCount(Level world) {
        return GamePlayerState.getOptedOutPlayerCount(world);
    }

    public static long getAlivePlayerCount(Level level) {
        return GamePlayerState.getAlivePlayerCount(level);
    }

    public static void reJudgeSpectatorsPenalty(Level world) {
        GamePlayerState.reJudgeSpectatorsPenalty(world);
    }

    public static void pendingReJudgeSpectatorsPenalty() {
        GamePlayerState.pendingReJudgeSpectatorsPenalty();
    }

    public static boolean shouldGiveSpectatorsDeathPenalty(Player player) {
        return GamePlayerState.shouldGiveSpectatorsDeathPenalty(player);
    }

    public static String spectatorsDeathPenaltyMessageKey(Player player) {
        return GamePlayerState.spectatorsDeathPenaltyMessageKey(player);
    }

    public static void refreshPlayerDimension(ServerPlayer player) {
        GamePlayerState.refreshPlayerDimension(player);
    }

    // 死亡、尸体和复活。
    public static PlayerBodyEntity spawnBodyEntity(Player victim, @Nullable Player killer, @Nullable SRERole role,
            ResourceLocation reason, boolean saveItems) {
        return GameDeath.spawnBodyEntity(victim, killer, role, reason, saveItems);
    }

    public static PlayerBodyEntity spawnBodyEntity(Player victim, @Nullable Player killer, @Nullable SRERole role,
            ResourceLocation reason) {
        return GameDeath.spawnBodyEntity(victim, killer, role, reason);
    }

    public static PlayerBodyEntity spawnBodyEntity(Player victim, @Nullable Player killer, @Nullable SRERole role,
            ResourceLocation reason, boolean saveItems, Vec3 spawnPos, float yHeadRot, float xHeadRot) {
        return GameDeath.spawnBodyEntity(victim, killer, role, reason, saveItems, spawnPos, yHeadRot, xHeadRot);
    }

    @Nullable
    public static PlayerBodyEntity findPlayerBodyEntity(ServerPlayer player) {
        return GameDeath.findPlayerBodyEntity(player);
    }

    public static void killPlayer(Player victim, boolean spawnBody, @Nullable Player killer) {
        killPlayer(victim, spawnBody, killer, GameConstants.DeathReasons.GENERIC);
    }

    public static void killPlayer(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation reason) {
        killPlayer(victim, spawnBody, killer, reason, false);
    }

    public static void forceKillPlayer(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation reason) {
        killPlayer(victim, spawnBody, killer, reason, true);
    }

    public static void killPlayer(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation reason, boolean forceDeath) {
        GameDeath.killPlayer(victim, spawnBody, killer, reason, forceDeath);
    }

    /** 死亡链内部的强杀上下文，嵌套调用及异常退出都会恢复外层状态。 */
    public static boolean isForcedDeathInProgress() {
        return GameDeath.isForcedDeathInProgress();
    }

    public static void revivePlayerToItsRoom(ServerPlayer player) {
        GameDeath.revivePlayerToItsRoom(player);
    }

    public static void revivePlayer(ServerPlayer player, double x, double y, double z) {
        GameDeath.revivePlayer(player, x, y, z);
    }

    // 地图、位置、命令与时间查询。
    public static void teleportBackToRoom(Player player) {
        GameWorld.teleportBackToRoom(player);
    }

    public static void teleportToRandomRoom(Player player) {
        GameWorld.teleportToRandomRoom(player);
    }

    public static Vec3 getSpawnPos(AreasWorldComponent areas, int room) {
        return GameWorld.getSpawnPos(areas, room);
    }

    /**
     * 房间号尚未分配时返回 null，避免把 {@code Map.get} 的 null 结果拆箱成 int。
     * 训练模式、地图切换等场景不会为玩家写入 {@link #roomToPlayer}；
     * 按玩家取出生点请统一使用 {@link #getPlayerRoomSpawnPos(Player)}。
     */
    public static Vec3 getSpawnPos(AreasWorldComponent areas, @Nullable Integer room) {
        if (room == null) return null;
        return getSpawnPos(areas, room.intValue());
    }

    /**
     * 「回房间」统一入口。规定：训练场假人的出生点房间就是它的初始点位；
     * 其他玩家按 {@link #roomToPlayer} 中的房间锚点；未分配房间返回 null。
     * 所有传送回房间、取自己房间坐标的逻辑都必须走这里，
     * 不要把 {@code roomToPlayer.get(uuid)} 直接当 int 用。
     */
    public static @Nullable Vec3 getPlayerRoomSpawnPos(Player player) {
        if (player == null || player.level() == null) return null;
        Vec3 trainingSpawn = TrainingRules.botRoomSpawn(player);
        if (trainingSpawn != null) return trainingSpawn;
        return getSpawnPos(AreasWorldComponent.KEY.get(player.level()), roomToPlayer.get(player.getUUID()));
    }

    public static void limitPlayerToBox(ServerPlayer player, AABB box) {
        GameWorld.limitPlayerToBox(player, box);
    }

    public static void resetEntities(ServerLevel world) {
        GameWorld.resetEntities(world);
    }

    public static boolean isTmmDoorBlock(Block block) {
        return GameWorld.isTmmDoorBlock(block);
    }

    public static void executeCommand(CommandSourceStack source, String command) {
        GameWorld.executeCommand(source, command);
    }

    public static void executeFunction(MinecraftServer server, int permission, String function) {
        GameWorld.executeFunction(server, permission, function);
    }

    public static void executeFunction(CommandSourceStack source, String function) {
        GameWorld.executeFunction(source, function);
    }

    public static boolean isGameRunning(Player player) {
        return GameWorld.isGameRunning(player);
    }

    public static boolean isGameRunning(Level level) {
        return GameWorld.isGameRunning(level);
    }

    public static boolean isTimeFrozen(MinecraftServer server) {
        return GameWorld.isTimeFrozen(server);
    }

    public static boolean isTimeFrozen(Level world) {
        return GameWorld.isTimeFrozen(world);
    }

    public static long getTicksFromGameStart(Level world) {
        return GameWorld.getTicksFromGameStart(world);
    }

    public static boolean isPlayerInLobby(ServerPlayer player) {
        return GameWorld.isPlayerInLobby(player);
    }

    public static boolean isUnderSprinklers(Player player) {
        return GameWorld.isUnderSprinklers(player);
    }

    // 物品冷却、掉落与弹药。
    public static void addItemCooldowns(ServerLevel world, int time) {
        GameItems.addItemCooldowns(world, time);
    }

    public static boolean shouldDropOnDeath(@NotNull ItemStack stack) {
        return GameItems.shouldDropOnDeath(stack);
    }

    public static boolean haveUsedoutDerringer(Player player) {
        return GameItems.haveUsedoutDerringer(player);
    }

    public static boolean refillDerringer(Player player, boolean haveBullet) {
        return GameItems.refillDerringer(player, haveBullet);
    }

    // 击杀记录与战绩。
    public static void recordPlayerKill(Player killer, Player victim, ResourceLocation reason, boolean haveDead) {
        GameStatistics.recordPlayerKill(killer, victim, reason, haveDead);
    }

    public static void recordPlayerKillServer(ServerPlayer killer, ServerPlayer victim,
            ResourceLocation reason, boolean haveDead) {
        GameStatistics.recordPlayerKillServer(killer, victim, reason, haveDead);
    }

    public static PlayerKillInfo getPlayerKillsInfo(UUID player) {
        return GameStatistics.getPlayerKillsInfo(player);
    }

    public static PlayerKillInfo getPlayerKillsInfo(Player player) {
        return GameStatistics.getPlayerKillsInfo(player);
    }

    public static PlayerKillResultInfo getPlayerLastDeathInfo(UUID player) {
        return GameStatistics.getPlayerLastDeathInfo(player);
    }

    public static PlayerKillResultInfo getPlayerLastDeathInfo(Player player) {
        return GameStatistics.getPlayerLastDeathInfo(player);
    }

    public static PlayerKillResultInfo getPlayerLastKillInfo(UUID player) {
        return GameStatistics.getPlayerLastKillInfo(player);
    }

    public static PlayerKillResultInfo getPlayerLastKillInfo(Player player) {
        return GameStatistics.getPlayerLastKillInfo(player);
    }

    public static void recordPlayerStats(ServerLevel world, SREGameWorldComponent component,
            ArrayList<ServerPlayer> players) {
        GameStatistics.recordPlayerStats(world, component, players);
    }

    public static void recordWinStats(ServerLevel world, GameMode mode, SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent component) {
        GameStatistics.recordWinStats(world, mode, roundEnd, component);
    }

    public enum SPAliveResult {
        ALIVE, DEAD, NOT
    }

    public record BlockEntityInfo(CompoundTag nbt, DataComponentMap components) {
    }

    public record BlockInfo(BlockPos pos, BlockState state, @Nullable BlockEntityInfo blockEntityInfo) {
    }

    public enum WinStatus {
        NOT_MODIFY, NONE, KILLERS, PASSENGERS, TIME, LOOSE_END, GAMBLER, RECORDER, NO_PLAYER, NIAN_SHOU, LOVERS,
        CUSTOM_COMPONENT, CUSTOM;

        public boolean isKillerWin() {
            return this.equals(WinStatus.KILLERS);
        }

        public boolean isInnocentWin() {
            return this.equals(WinStatus.TIME) || this.equals(WinStatus.PASSENGERS);
        }
    }

    public static record PlayerKillResultInfo(long time, boolean dead, UUID killer, UUID victim,
            ResourceLocation deathReason) {
    }

    public static class PlayerKillInfo {
        public PlayerKillInfo() {
        }

        public PlayerKillResultInfo getLastDeathInfo() {
            if (deaths.isEmpty()) return null;
            return deaths.getLast();
        }

        public ArrayList<PlayerKillResultInfo> deaths = new ArrayList<>();
        public ArrayList<PlayerKillResultInfo> kills = new ArrayList<>();

        public void recordDeath(PlayerKillResultInfo info) {
            this.deaths.addLast(info);
        }

        public void recordKill(PlayerKillResultInfo info) {
            this.kills.addLast(info);
        }

        public PlayerKillResultInfo getLastKillInfo() {
            if (kills.isEmpty()) return null;
            return kills.getLast();
        }
    }
}
