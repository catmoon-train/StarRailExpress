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

package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import io.wifi.starrailexpress.network.packet.RoleRotationSyncS2CPacket;
import net.minecraft.client.Minecraft;
import java.util.*;

public class RoleRotationCache {

    // ==================== 全局状态 ====================
    private static boolean isSelecting = false;
    private static int currentRoundIndex = 0; // 当前轮次（从1开始）
    private static int totalPlayerCount = 0;
    private static int confirmCountdown = -1; // 确认阶段倒计时（tick）
    private static int perPlayerTimeLimit = 0; // 每个玩家的选择时限（tick）
    private static long roundStartGameTime = 0; // 服务端本轮开始时的世界游戏时间（tick）

    // ==================== 玩家数据 ====================
    private static final List<UUID> playerOrder = new ArrayList<>(); // 全局玩家顺序
    private static final Map<UUID, Integer> rotationOrder = new LinkedHashMap<>(); // UUID -> 序号
    private static final Map<UUID, String> selectedRoles = new LinkedHashMap<>(); // UUID -> 角色ID字符串
    private static final Set<UUID> randomChoosers = new HashSet<>();
    private static final Map<UUID, List<String>> roundCandidates = new LinkedHashMap<>(); // 本轮玩家 -> 候选角色ID列表

    // ==================== 缓存辅助 ====================
    private static UUID localPlayerUuid = null; // 本地玩家 UUID
    private static boolean wasMyTurn = false; // 用于音效/提示

    // ---------- 网络包更新 ----------
    public static void updateFromPacket(RoleRotationSyncS2CPacket packet) {
        isSelecting = packet.isSelecting();
        currentRoundIndex = packet.currentRoundIndex();
        totalPlayerCount = packet.totalPlayerCount();
        confirmCountdown = packet.confirmCountdown();
        perPlayerTimeLimit = packet.perPlayerTimeLimit();
        roundStartGameTime = packet.roundStartTime();

        // 玩家顺序
        playerOrder.clear();
        playerOrder.addAll(packet.playerOrder());
        rotationOrder.clear();
        for (int i = 0; i < packet.playerOrder().size(); i++) {
            rotationOrder.put(packet.playerOrder().get(i), i + 1);
        }

        // 已选职业
        selectedRoles.clear();
        selectedRoles.putAll(packet.selectedRoles());

        // 随机选择者
        randomChoosers.clear();
        randomChoosers.addAll(packet.randomChoosers());

        // 本轮候选映射
        roundCandidates.clear();
        roundCandidates.putAll(packet.roundCandidates());

        // 本地玩家 UUID（动态获取，以防切换账号）
        Minecraft mc = Minecraft.getInstance();
        localPlayerUuid = mc.player != null ? mc.player.getUUID() : null;

        // 本轮是否轮到自己
        wasMyTurn = isSelecting && localPlayerUuid != null && roundCandidates.containsKey(localPlayerUuid);
    }

    // ---------- 客户端每帧调用 ----------
    public static void tickTimers() {
        // 确认倒计时本地递减，确保 UI 平滑
        if (confirmCountdown > 0) {
            confirmCountdown--;
        }
    }

    // ---------- 状态查询 ----------
    public static boolean isSelecting() {
        return isSelecting;
    }

    public static int getCurrentRoundIndex() {
        return currentRoundIndex;
    }

    public static int getTotalPlayers() {
        return totalPlayerCount;
    }

    public static int getConfirmCountdown() {
        return confirmCountdown;
    }

    /**
     * 个人剩余选择时间（tick）
     * 使用客户端 level 的游戏时间与服务端 roundStartTime 之差。
     */
    public static int getRemainingTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return 0;
        long elapsed = mc.level.getGameTime() - roundStartGameTime;
        return (int) Math.max(0, perPlayerTimeLimit - elapsed);
    }

    public static int getRemainingSeconds() {
        return getRemainingTime() / 20;
    }

    // 当前选角阶段剩余秒数（用于显示）
    public static int getDisplaySeconds() {
        if (isSelecting && isMyTurn()) {
            if (!hasSelected())
                return getRemainingSeconds();
            return 0;
        } else if (isSelecting) {
            return getRemainingSeconds();
        } else if (confirmCountdown > 0) {
            return confirmCountdown / 20;
        }
        return 0;
    }

    // 全局玩家列表
    public static Map<UUID, Integer> getRotationOrder() {
        return rotationOrder;
    }

    public static Map<UUID, String> getSelectedRoles() {
        return selectedRoles;
    }

    public static Set<UUID> getRandomChoosers() {
        return randomChoosers;
    }

    public static Map<UUID, List<String>> getRoundCandidates() {
        return roundCandidates;
    }

    // 当前本地玩家的候选职业列表
    public static List<String> getMyCandidates() {
        if (localPlayerUuid == null)
            return Collections.emptyList();
        return roundCandidates.getOrDefault(localPlayerUuid, Collections.emptyList());
    }

    // 当前是否轮到本地玩家（即在 roundCandidates 中）
    public static boolean isMyTurn() {
        return localPlayerUuid != null && roundCandidates.containsKey(localPlayerUuid);
    }

    // 本地玩家是否已选择职业
    public static boolean hasSelected() {
        return localPlayerUuid != null && selectedRoles.containsKey(localPlayerUuid);
    }

    // 兼容旧方法
    public static int getCurrentIndex() {
        return currentRoundIndex;
    }

    public static int getMyRotationIndex() {
        return localPlayerUuid != null ? rotationOrder.getOrDefault(localPlayerUuid, -1) : -1;
    }

    public static boolean getWasMyTurn() {
        return wasMyTurn;
    }

    // 是否仍可打开轮选界面
    public static boolean canReOpen() {
        return isSelecting || confirmCountdown > 0;
    }

    // 清空（游戏结束）
    public static void clear() {
        isSelecting = false;
        currentRoundIndex = 0;
        totalPlayerCount = 0;
        confirmCountdown = -1;
        perPlayerTimeLimit = 0;
        roundStartGameTime = 0;
        playerOrder.clear();
        rotationOrder.clear();
        selectedRoles.clear();
        randomChoosers.clear();
        roundCandidates.clear();
        localPlayerUuid = null;
        wasMyTurn = false;
        final var mc = Minecraft.getInstance();
        if (mc.screen instanceof RoleRotationScreen) {
            mc.setScreen(null);
        }
    }

    public static void updateBaseState(boolean selecting, int index, int total, int countdown) {
        isSelecting = selecting;
        currentRoundIndex = index;
        totalPlayerCount = total;
        confirmCountdown = countdown;
    }
}