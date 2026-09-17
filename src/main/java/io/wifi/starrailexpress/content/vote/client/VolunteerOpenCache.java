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

import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.VolunteerOpenSelectScreen;
import io.wifi.starrailexpress.network.packet.VolunteerOpenSyncS2CPacket;
import net.minecraft.client.Minecraft;

import java.util.*;

/**
 * 志愿海选模式的客户端状态缓存。
 *
 * <p>
 * 服务端逐玩家发送同步包，因为海选池的揭晓状态因人而异。
 */
public class VolunteerOpenCache {

    public static final int PHASE_INACTIVE = 0;
    public static final int PHASE_VOLUNTEER = 1;
    public static final int PHASE_OPEN = 2;
    public static final int PHASE_CONFIRM = 3;

    // ==================== 全局状态 ====================
    private static boolean active = false;
    private static int phase = PHASE_INACTIVE;
    private static int totalPlayerCount = 0;
    /** 当前阶段剩余 tick；-1 表示服务端还在等玩家打开界面，尚未开始计时。 */
    private static int remainingTicks = -1;
    private static int phaseTimeLimit = 0;
    private static int confirmCountdown = -1;
    private static boolean confirmRequired = false;
    /** 本局是否已经上报过「界面已打开」。 */
    private static boolean uiReported = false;

    // ==================== 玩家数据 ====================
    private static final List<UUID> playerOrder = new ArrayList<>();
    private static final Map<UUID, Integer> rotationOrder = new LinkedHashMap<>();
    private static final Map<UUID, String> pickedRoles = new LinkedHashMap<>();
    private static final Set<UUID> confirmedPlayers = new HashSet<>();

    // ==================== 海选池 ====================
    private static int poolSize = 0;
    private static int poolRows = 1;
    private static int poolCols = 1;
    private static final Map<Integer, String> visibleRoles = new LinkedHashMap<>();
    private static final Set<Integer> cardRevealed = new HashSet<>();
    private static final Set<Integer> hiddenRevealed = new HashSet<>();
    private static int myPickIndex = -1;
    private static final Set<Integer> chosenIndices = new HashSet<>();
    private static int myVolunteerIndex = -1;
    private static String myVolunteerRoleId = "";

    // ==================== 分组 ====================
    private static int currentGroup = 0;
    private static int totalGroups = 0;
    private static boolean canSelect = false;
    private static final Set<UUID> currentGroupMembers = new HashSet<>();

    private static UUID localPlayerUuid = null;

    private VolunteerOpenCache() {
    }

    // ---------- 网络包更新 ----------
    public static void updateFromPacket(VolunteerOpenSyncS2CPacket packet) {
        phase = packet.phase();
        active = phase != PHASE_INACTIVE;
        if (!active) {
            // 本局结束：允许下一局重新上报「界面已打开」
            uiReported = false;
        }
        totalPlayerCount = packet.totalPlayers();
        remainingTicks = packet.remainingTicks();
        phaseTimeLimit = packet.phaseTimeLimit();
        confirmCountdown = packet.confirmCountdown();
        confirmRequired = packet.confirmRequired();

        playerOrder.clear();
        playerOrder.addAll(packet.playerOrder());
        rotationOrder.clear();
        for (int i = 0; i < playerOrder.size(); i++) {
            rotationOrder.put(playerOrder.get(i), i + 1);
        }

        pickedRoles.clear();
        pickedRoles.putAll(packet.pickedRoles());

        confirmedPlayers.clear();
        confirmedPlayers.addAll(packet.confirmedPlayers());

        poolSize = packet.poolSize();
        poolRows = Math.max(1, packet.poolRows());
        poolCols = Math.max(1, packet.poolCols());

        visibleRoles.clear();
        visibleRoles.putAll(packet.visibleRoles());

        cardRevealed.clear();
        cardRevealed.addAll(packet.cardRevealed());
        hiddenRevealed.clear();
        hiddenRevealed.addAll(packet.hiddenRevealed());

        myPickIndex = packet.myPickIndex();
        chosenIndices.clear();
        chosenIndices.addAll(packet.chosenIndices());
        myVolunteerIndex = packet.myVolunteerIndex();
        myVolunteerRoleId = packet.myVolunteerRoleId() == null ? "" : packet.myVolunteerRoleId();

        currentGroup = packet.currentGroup();
        totalGroups = packet.totalGroups();
        canSelect = packet.canSelect();
        currentGroupMembers.clear();
        currentGroupMembers.addAll(packet.currentGroupMembers());

        Minecraft mc = Minecraft.getInstance();
        localPlayerUuid = mc.player != null ? mc.player.getUUID() : null;

        // 让开场演示流程（OpeningPresentationCoordinator / SREClient）把本模式视为
        // 「轮选进行中」，从而复用轮选模式的运镜等待、welcome 暂停与界面顶出逻辑。
        if (active) {
            RoleRotationCache.updateBaseState(
                    phase == PHASE_VOLUNTEER || phase == PHASE_OPEN,
                    Math.max(1, currentGroup),
                    totalPlayerCount,
                    confirmCountdown);
        } else {
            // 本局流程结束（服务端终态同步）：收尾并关闭界面
            RoleRotationCache.finishRotation();
            finish();
        }
    }

    // ---------- 客户端每帧调用 ----------
    public static void tickTimers() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        if (confirmCountdown > 0) {
            confirmCountdown--;
        }
    }

    // ---------- 状态查询 ----------
    /** 本模式是否正在进行（用于选择要打开的界面）。 */
    public static boolean isVolunteerOpenMode() {
        return active;
    }

    /** 界面是否应当保持打开。 */
    public static boolean canReOpen() {
        return active;
    }

    public static int getPhase() {
        return phase;
    }

    public static int getTotalPlayers() {
        return totalPlayerCount;
    }

    public static int getConfirmCountdown() {
        return confirmCountdown;
    }

    public static boolean isConfirmRequired() {
        return confirmRequired;
    }

    public static Set<UUID> getConfirmedPlayers() {
        return Collections.unmodifiableSet(confirmedPlayers);
    }

    public static boolean isLocalPlayerConfirmed() {
        return localPlayerUuid != null && confirmedPlayers.contains(localPlayerUuid);
    }

    public static Map<UUID, Integer> getRotationOrder() {
        return rotationOrder;
    }

    public static Map<UUID, String> getPickedRoles() {
        return pickedRoles;
    }

    public static int getPoolSize() {
        return poolSize;
    }

    public static int getPoolRows() {
        return poolRows;
    }

    public static int getPoolCols() {
        return poolCols;
    }

    public static Map<Integer, String> getVisibleRoles() {
        return visibleRoles;
    }

    public static Set<Integer> getCardRevealed() {
        return cardRevealed;
    }

    public static Set<Integer> getHiddenRevealed() {
        return hiddenRevealed;
    }

    public static int getMyPickIndex() {
        return myPickIndex;
    }

    public static Set<Integer> getChosenIndices() {
        return chosenIndices;
    }

    public static int getMyVolunteerIndex() {
        return myVolunteerIndex;
    }

    public static String getMyVolunteerRoleId() {
        return myVolunteerRoleId;
    }

    public static int getCurrentGroup() {
        return currentGroup;
    }

    public static int getTotalGroups() {
        return totalGroups;
    }

    public static boolean canSelect() {
        return canSelect;
    }

    public static Set<UUID> getCurrentGroupMembers() {
        return Collections.unmodifiableSet(currentGroupMembers);
    }

    public static int getMyIndex() {
        return localPlayerUuid != null ? rotationOrder.getOrDefault(localPlayerUuid, -1) : -1;
    }

    /**
     * 当前阶段是否还没开始计时（还在等玩家打开界面）。
     * 界面在此时显示「准备中」而不是倒计时。
     */
    public static boolean isTimerHeld() {
        return active && phase != PHASE_CONFIRM && remainingTicks < 0;
    }

    /** 当前阶段剩余时间（tick）。 */
    public static int getRemainingTime() {
        if (remainingTicks >= 0) {
            return remainingTicks;
        }
        return phaseTimeLimit;
    }

    public static int getRemainingSeconds() {
        return getRemainingTime() / 20;
    }

    /** 界面上要显示的倒计时秒数。 */
    public static int getDisplaySeconds() {
        if (phase == PHASE_CONFIRM) {
            return confirmCountdown > 0 ? confirmCountdown / 20 : 0;
        }
        if (active) {
            return getRemainingSeconds();
        }
        return 0;
    }

    /** 本局是否还没有上报过「界面已打开」。 */
    public static boolean markUiReported() {
        if (uiReported) {
            return false;
        }
        uiReported = true;
        return true;
    }

    // ---------- 清理 ----------
    /** 本局结束：关闭界面并清空状态。 */
    public static void finish() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VolunteerOpenSelectScreen) {
            mc.setScreen(null);
        }
        clear();
    }

    public static void clear() {
        active = false;
        phase = PHASE_INACTIVE;
        totalPlayerCount = 0;
        remainingTicks = -1;
        phaseTimeLimit = 0;
        uiReported = false;
        confirmCountdown = -1;
        confirmRequired = false;
        playerOrder.clear();
        rotationOrder.clear();
        pickedRoles.clear();
        confirmedPlayers.clear();
        poolSize = 0;
        poolRows = 1;
        poolCols = 1;
        visibleRoles.clear();
        cardRevealed.clear();
        hiddenRevealed.clear();
        myPickIndex = -1;
        chosenIndices.clear();
        myVolunteerIndex = -1;
        myVolunteerRoleId = "";
        currentGroup = 0;
        totalGroups = 0;
        canSelect = false;
        currentGroupMembers.clear();
    }
}
