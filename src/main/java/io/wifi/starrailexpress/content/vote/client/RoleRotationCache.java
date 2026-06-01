package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.network.RoleRotationSyncS2CPacket;

import java.util.*;

/**
 * 职业轮选模式客户端缓存
 */
public class RoleRotationCache {

    private static boolean isSelecting = false;
    private static int currentIndex = 0;
    private static int totalPlayers = 0;
    private static int confirmCountdown = -1;
    private static int finalPhaseThreshold = 6;
    private static int remainingTime = 0;

    // 玩家序号映射 (玩家UUID -> 序号)
    private static final HashMap<UUID, Integer> rotationOrder = new HashMap<>();

    // 已选择职业的玩家 (玩家UUID -> 职业名)
    private static final HashMap<UUID, String> selectedRoles = new HashMap<>();

    // 当前候选职业
    private static final List<String> currentCandidates = new ArrayList<>();

    // 当前玩家自己的序号
    private static int myRotationIndex = -1;
    
    // 随机选择玩家集合（用于鹅鸭杀轮抽模式）
    private static final Set<UUID> randomChoosers = new HashSet<>();
    
    // 上次是否为轮到当前玩家（用于检测状态变化）
    private static boolean wasMyTurn = false;

    public static void updateFromPacket(RoleRotationSyncS2CPacket packet) {
        isSelecting = packet.isSelecting();
        currentIndex = packet.getCurrentIndex();
        // 确保totalPlayers被正确更新
        if (packet.getTotalPlayers() > 0) {
            totalPlayers = packet.getTotalPlayers();
        }
        confirmCountdown = packet.getConfirmCountdown();
        finalPhaseThreshold = packet.getFinalPhaseThreshold();
        remainingTime = packet.getRemainingTime();
        
        // 更新rotationOrder
        rotationOrder.clear();
        rotationOrder.putAll(packet.getRotationOrder());
        
        // 更新selectedRoles
        selectedRoles.clear();
        selectedRoles.putAll(packet.getSelectedRoles());
        
        // 更新currentCandidates
        currentCandidates.clear();
        currentCandidates.addAll(packet.getCurrentCandidates());
        
        // 更新myRotationIndex（支持0号玩家）
        if (packet.getMyRotationIndex() >= 0) {
            myRotationIndex = packet.getMyRotationIndex();
        }
        
        // 更新随机选择玩家
        randomChoosers.clear();
        randomChoosers.addAll(packet.getRandomChoosers());
        
        // 更新轮到状态
        wasMyTurn = isSelecting && isMyTurnLocal();
    }
    
    // 检查当前玩家（通过myRotationIndex）是否是当前轮到的玩家
    private static boolean isMyTurnLocal() {
        return myRotationIndex >= 0 && myRotationIndex == currentIndex;
    }
    
    public static boolean getWasMyTurn() {
        return wasMyTurn;
    }

    public static boolean isSelecting() {
        return isSelecting;
    }

    public static int getCurrentIndex() {
        return currentIndex;
    }

    public static int getTotalPlayers() {
        return totalPlayers;
    }

    public static int getConfirmCountdown() {
        return confirmCountdown;
    }

    public static int getFinalPhaseThreshold() {
        return finalPhaseThreshold;
    }

    public static int getRemainingTime() {
        return remainingTime;
    }

    public static int getRemainingSeconds() {
        // 如果正在选择，使用remainingTime；否则使用confirmCountdown
        if (isSelecting) {
            return remainingTime / 20;
        } else if (confirmCountdown > 0) {
            return confirmCountdown / 20;
        }
        return remainingTime / 20;
    }

    public static HashMap<UUID, Integer> getRotationOrder() {
        return rotationOrder;
    }

    public static HashMap<UUID, String> getSelectedRoles() {
        return selectedRoles;
    }

    public static List<String> getCurrentCandidates() {
        return currentCandidates;
    }

    public static int getMyRotationIndex() {
        return myRotationIndex;
    }
    
    public static void setMyRotationIndex(int index) {
        myRotationIndex = index;
    }

    public static Set<UUID> getRandomChoosers() {
        return randomChoosers;
    }

    public static void updateRandomChoosers(Set<UUID> choosers) {
        randomChoosers.clear();
        randomChoosers.addAll(choosers);
    }

    public static boolean isMyTurn(UUID playerUuid) {
        Integer index = rotationOrder.get(playerUuid);
        return index != null && index == currentIndex;
    }

    public static void updateRotationOrder(HashMap<UUID, Integer> order) {
        rotationOrder.clear();
        rotationOrder.putAll(order);
    }

    public static void updateSelectedRoles(HashMap<UUID, String> selected) {
        selectedRoles.clear();
        selectedRoles.putAll(selected);
    }

    public static void updateCurrentCandidates(List<String> candidates) {
        currentCandidates.clear();
        currentCandidates.addAll(candidates);
    }

    public static void clear() {
        isSelecting = false;
        currentIndex = 0;
        totalPlayers = 0;
        confirmCountdown = -1;
        remainingTime = 0;
        rotationOrder.clear();
        selectedRoles.clear();
        currentCandidates.clear();
        randomChoosers.clear();
        myRotationIndex = -1;
    }

    public static boolean canReOpen() {
        // isSelecting 为 true 时可以重新打开，或者确认倒计时 > 0 时可以打开
        // 确认倒计时 <= 0 时表示轮选已结束，不应该重新打开界面
        return isSelecting || confirmCountdown > 0;
    }
    
    public static void updateBaseState(boolean selecting, int currentIdx, int total, int countdown) {
        isSelecting = selecting;
        currentIndex = currentIdx;
        totalPlayers = total;
        confirmCountdown = countdown;
    }
}
