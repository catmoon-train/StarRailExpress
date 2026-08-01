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

package io.wifi.starrailexpress.game.modes.funny.rotation;

import java.util.*;
import java.util.stream.Collectors;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import net.exmo.sre.repair.role.RepairRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 职业轮抽单选模式的状态管理。
 * 玩家按顺序逐一选择职业（每人从 3 个候选中选 1 个 + 随机），与闪电轮抽的并行模式不同。
 */
public class SingleSelectDraftState {

    // ===== 职业池与结果 =====
    public final ArrayList<SRERole> rolePool = new ArrayList<>();
    public final Map<UUID, SRERole> selectedRoles = new LinkedHashMap<>();
    public final Set<UUID> randomChoosers = new HashSet<>();

    // ===== 玩家顺序（1-based）=====
    public final Map<UUID, Integer> playerOrder = new LinkedHashMap<>();

    // ===== 选择控制 =====
    public int totalPlayers;
    public int currentRotationIndex = 1;
    public int confirmCountdown = -1;
    public long roundStartTime = -1;
    public int perPlayerTimeLimit = 4 * 20; // 每人 4 秒
    public boolean isSelecting = false;

    // ===== 当前玩家候选 =====
    private final ArrayList<SRERole> currentCandidates = new ArrayList<>();

    // ===== 最后阶段阈值 =====
    private int finalPhaseThreshold = 6;

    // ===== 卡片追踪 =====
    private final Map<Integer, Integer> cardUsedCount = new HashMap<>();
    private final Map<Integer, Integer> cardMaxPerType = new HashMap<>();

    // ===== 特殊平民职业 =====
    private static final Set<SRERole> SPECIAL_CIVILIAN_ROLES = Set.of(
            ModRoles.DIVER,
            ModRoles.DOCTOR,
            ModRoles.PILOT,
            RedHouseRoles.BAKA,
            RedHouseRoles.PACHURI,
            ModRoles.FITTER);

    // ---------- 初始化角色池 ----------
    public void initializeRolePool(ServerLevel world) {
        rolePool.clear();
        totalPlayers = playerOrder.size();

        if (totalPlayers <= 12) {
            finalPhaseThreshold = 6;
        } else if (totalPlayers < 24) {
            finalPhaseThreshold = (int) Math.ceil(totalPlayers / 2.0);
        } else {
            finalPhaseThreshold = (int) Math.floor(totalPlayers / 2.0 + totalPlayers / 7.0);
        }

        int killerCount = RoleCountManager.getKillerCount(totalPlayers);
        int vigilanteCount = RoleCountManager.getVigilanteCount(totalPlayers);
        int neutralsCount = RoleCountManager.getNeutralCount(totalPlayers);
        killerCount = Math.max(1, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;

        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        !RoleUtils.compareRole(role, ModRoles.PUPPETEER) &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() &&
                        !role.isOtherModeRole() && !(role instanceof RepairRole));
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        ((!role.canUseKiller() && !role.isInnocent()) || role.isNeutrals()) &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        (enableCivilianInPool || role != TMMRoles.CIVILIAN));
        if (enableCivilianInPool) {
            Harpymodloader.setRoleMaximum(TMMRoles.CIVILIAN.getIdentifier(), 1);
        }

        List<RoleInstance> baseRoles = SREMurderGameMode.getAllRoles(killerCount, vigilanteCount, neutralsCount,
                totalPlayers + 5, 0, killerPool, neutralsPool, vigilantePool, civilianPool, true);

        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null) {
                rolePool.add(inst.role());
            }
        }

        initializeCardTracking();
    }

    // ---------- 卡片追踪 ----------
    private void initializeCardTracking() {
        cardUsedCount.clear();
        cardMaxPerType.clear();

        int limit = Math.max(1, totalPlayers / 7);
        cardMaxPerType.put(4, limit);
        cardMaxPerType.put(2, limit);
        cardMaxPerType.put(1, limit);

        Map<Integer, List<UUID>> byType = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : playerOrder.entrySet()) {
            UUID uuid = entry.getKey();
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(uuid);
            if (forcedType != null) {
                int normalized = normalizeCardType(forcedType);
                byType.computeIfAbsent(normalized, k -> new ArrayList<>()).add(uuid);
            }
        }
        for (Map.Entry<Integer, List<UUID>> entry : byType.entrySet()) {
            int type = entry.getKey();
            List<UUID> uuids = entry.getValue();
            int max = cardMaxPerType.getOrDefault(type, 0);
            cardUsedCount.put(type, Math.min(uuids.size(), max));
            for (int i = max; i < uuids.size(); i++) {
                PlayerRoleWeightManager.ForcePlayerTeam.remove(uuids.get(i));
            }
        }
    }

    private static int normalizeCardType(int rawType) {
        return switch (rawType) {
            case 5 -> 1;
            case 3 -> 2;
            default -> rawType;
        };
    }

    private int getPlayerCardType(UUID uuid) {
        Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(uuid);
        return forcedType != null ? normalizeCardType(forcedType) : -1;
    }

    // ---------- 玩家顺序 ----------
    public void assignRotationOrder() {
        List<UUID> sortedPlayers = new ArrayList<>(playerOrder.keySet());
        int n = sortedPlayers.size();
        Random random = new Random();

        // 按卡片类型分组
        List<UUID> killerCardUsers = new ArrayList<>();
        List<UUID> neutralCardUsers = new ArrayList<>();
        List<UUID> civilianCardUsers = new ArrayList<>();
        List<UUID> noCardUsers = new ArrayList<>();

        for (UUID uuid : sortedPlayers) {
            int card = getPlayerCardType(uuid);
            switch (card) {
                case 0: killerCardUsers.add(uuid); break;
                case 1: neutralCardUsers.add(uuid); break;
                case 2: civilianCardUsers.add(uuid); break;
                default: noCardUsers.add(uuid); break;
            }
        }

        Collections.shuffle(killerCardUsers, random);
        Collections.shuffle(neutralCardUsers, random);
        Collections.shuffle(civilianCardUsers, random);
        Collections.shuffle(noCardUsers, random);

        int killerStart = (int) Math.floor(n * 0.4);
        int killerEnd = Math.min((int) Math.ceil(n * 0.5), n);
        int neutralStart = 0;
        int neutralEnd = Math.min((int) Math.ceil(n * 0.2), n);
        int civilianStart = (int) Math.floor(n * 0.7);
        int civilianEnd = n;

        Integer[] slots = new Integer[n];
        int nextSlot;

        nextSlot = neutralStart;
        for (UUID uuid : neutralCardUsers) {
            while (nextSlot < neutralEnd && slots[nextSlot] != null) nextSlot++;
            if (nextSlot < neutralEnd) {
                slots[nextSlot] = 1;
                playerOrder.put(uuid, nextSlot + 1);
                nextSlot++;
            }
        }
        for (UUID uuid : neutralCardUsers) {
            if (playerOrder.containsKey(uuid)) continue;
            fillNearestSlot(slots, uuid, n);
        }

        nextSlot = killerStart;
        for (UUID uuid : killerCardUsers) {
            while (nextSlot < killerEnd && slots[nextSlot] != null) nextSlot++;
            if (nextSlot < killerEnd) {
                slots[nextSlot] = 1;
                playerOrder.put(uuid, nextSlot + 1);
                nextSlot++;
            }
        }
        for (UUID uuid : killerCardUsers) {
            if (playerOrder.containsKey(uuid)) continue;
            fillNearestSlot(slots, uuid, n);
        }

        nextSlot = civilianStart;
        for (UUID uuid : civilianCardUsers) {
            while (nextSlot < civilianEnd && slots[nextSlot] != null) nextSlot++;
            if (nextSlot < civilianEnd) {
                slots[nextSlot] = 1;
                playerOrder.put(uuid, nextSlot + 1);
                nextSlot++;
            }
        }
        for (UUID uuid : civilianCardUsers) {
            if (playerOrder.containsKey(uuid)) continue;
            fillNearestSlot(slots, uuid, n);
        }

        for (UUID uuid : noCardUsers) {
            fillNearestSlot(slots, uuid, n);
        }
    }

    private void fillNearestSlot(Integer[] slots, UUID uuid, int n) {
        for (int i = 0; i < n; i++) {
            if (slots[i] == null) {
                slots[i] = 1;
                playerOrder.put(uuid, i + 1);
                return;
            }
        }
    }

    private UUID findPlayerByRotationIndex(int index) {
        for (Map.Entry<UUID, Integer> entry : playerOrder.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ---------- 轮次控制 ----------
    public void startNextRound(ServerLevel world) {
        currentRotationIndex = 1;
        isSelecting = true;
        confirmCountdown = -1;
        roundStartTime = world.getGameTime();
        UUID firstPlayer = findPlayerByRotationIndex(currentRotationIndex);
        if (firstPlayer != null) {
            prepareCandidatesForPlayer(firstPlayer);
        }
    }

    private void advanceToNextPlayer(ServerLevel world) {
        while (currentRotationIndex <= totalPlayers) {
            UUID nextPlayer = findPlayerByRotationIndex(currentRotationIndex);
            if (nextPlayer != null && !selectedRoles.containsKey(nextPlayer)) {
                prepareCandidatesForPlayer(nextPlayer);
                roundStartTime = world.getGameTime();
                return;
            }
            currentRotationIndex++;
        }

        if (currentRotationIndex > totalPlayers) {
            adjustRemainingRoles(world);
            startConfirmCountdown();
        }
    }

    private void startConfirmCountdown() {
        isSelecting = false;
        confirmCountdown = 6 * 20;
    }

    // ---------- 候选职业生成 ----------
    private void prepareCandidatesForPlayer(UUID playerUuid) {
        currentCandidates.clear();

        int remainingPlayers = totalPlayers - selectedRoles.size();
        boolean isFinalPhase = remainingPlayers <= finalPhaseThreshold;

        ArrayList<SRERole> poolCopy = new ArrayList<>(rolePool);
        Random random = new Random();

        int cardType = getPlayerCardType(playerUuid);
        boolean cardPriorityHandled = false;

        // 卡片用户优先处理
        if (cardType == 0 || cardType == 1) {
            ArrayList<SRERole> priorityRoles = new ArrayList<>();
            ArrayList<SRERole> otherRoles = new ArrayList<>();

            for (SRERole role : poolCopy) {
                int type = PlayerRoleWeightManager.getRoleType(role);
                if (cardType == 0 && type == 4) {
                    priorityRoles.add(role);
                } else if (cardType == 1 && (type == 2 || type == 3)) {
                    if (type == 2) priorityRoles.add(role);
                    else otherRoles.add(role);
                } else {
                    otherRoles.add(role);
                }
            }

            if (!priorityRoles.isEmpty() && priorityRoles.size() + otherRoles.size() >= 3) {
                Collections.shuffle(priorityRoles, random);
                int priorityCount = Math.min(2, priorityRoles.size());
                for (int i = 0; i < priorityCount; i++) {
                    currentCandidates.add(priorityRoles.get(i));
                    poolCopy.remove(priorityRoles.get(i));
                }
                Collections.shuffle(poolCopy, random);
                for (int i = 0; currentCandidates.size() < 3 && i < poolCopy.size(); i++) {
                    currentCandidates.add(poolCopy.get(i));
                }
                cardPriorityHandled = true;
            } else if (!otherRoles.isEmpty() && otherRoles.size() >= 3) {
                Collections.shuffle(otherRoles, random);
                for (int i = 0; i < 3; i++) {
                    currentCandidates.add(otherRoles.get(i));
                }
                cardPriorityHandled = true;
            }
        }

        if (!cardPriorityHandled) {
            if (isFinalPhase) {
                ArrayList<SRERole> priorityRoles = new ArrayList<>();
                for (SRERole role : poolCopy) {
                    int type = PlayerRoleWeightManager.getRoleType(role);
                    if (type == 4 || type == 5 || type == 2 || type == 3) {
                        priorityRoles.add(role);
                    } else if (isSpecialCivilianRole(role)) {
                        priorityRoles.add(role);
                    }
                }

                if (priorityRoles.size() >= 3) {
                    Collections.shuffle(priorityRoles, random);
                    for (int i = 0; i < 3; i++) {
                        currentCandidates.add(priorityRoles.get(i));
                    }
                } else {
                    Collections.shuffle(poolCopy, random);
                    for (int i = 0; i < 3 && i < poolCopy.size(); i++) {
                        currentCandidates.add(poolCopy.get(i));
                    }
                }
            } else {
                Collections.shuffle(poolCopy, random);
                for (int i = 0; i < 3 && i < poolCopy.size(); i++) {
                    currentCandidates.add(poolCopy.get(i));
                }
            }
        }
    }

    // ---------- 玩家选择处理 ----------
    public boolean processSelection(ServerLevel world, UUID playerUuid, int choiceIndex) {
        if (!isSelecting) return false;

        UUID expectedPlayer = findPlayerByRotationIndex(currentRotationIndex);
        if (expectedPlayer == null || !expectedPlayer.equals(playerUuid)) return false;

        SRERole selectedRole = null;
        if (choiceIndex >= 0 && choiceIndex < currentCandidates.size()) {
            selectedRole = currentCandidates.get(choiceIndex);
        } else if (choiceIndex == 3) {
            selectedRole = selectRandomRole();
            randomChoosers.add(playerUuid);
        }

        if (selectedRole == null) return false;

        selectedRoles.put(playerUuid, selectedRole);
        rolePool.remove(selectedRole);

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("gui.sre.role_rotation.selected",
                            RoleUtils.getRoleName(selectedRole).withColor(selectedRole.getColor()))
                            .withStyle(ChatFormatting.GREEN),
                    true);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);
        }

        currentRotationIndex++;
        advanceToNextPlayer(world);
        return true;
    }

    private SRERole selectRandomRole() {
        if (rolePool.isEmpty()) return TMMRoles.CIVILIAN;

        int remainingPlayers = totalPlayers - selectedRoles.size();
        boolean isFinalPhase = remainingPlayers <= finalPhaseThreshold;
        Random random = new Random();

        if (isFinalPhase) {
            ArrayList<SRERole> priorityPool = new ArrayList<>();
            for (SRERole role : rolePool) {
                int type = PlayerRoleWeightManager.getRoleType(role);
                if (type == 4 || type == 5 || type == 2 || type == 3) {
                    priorityPool.add(role);
                } else if (isSpecialCivilianRole(role)) {
                    priorityPool.add(role);
                }
            }
            if (!priorityPool.isEmpty()) {
                return priorityPool.get(random.nextInt(priorityPool.size()));
            }
        }

        return rolePool.get(random.nextInt(rolePool.size()));
    }

    // ---------- 超时处理 ----------
    public boolean isCurrentPlayerTimedOut(ServerLevel world) {
        if (!isSelecting || roundStartTime < 0) return false;
        return world.getGameTime() - roundStartTime >= perPlayerTimeLimit;
    }

    public void timeoutUnfinishedPlayers(ServerLevel world) {
        if (!isSelecting) return;

        UUID currentPlayerUuid = findPlayerByRotationIndex(currentRotationIndex);
        if (currentPlayerUuid == null) return;

        SRERole randomRole = selectRandomRole();
        if (randomRole == null) randomRole = TMMRoles.CIVILIAN;

        selectedRoles.put(currentPlayerUuid, randomRole);
        rolePool.remove(randomRole);

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(currentPlayerUuid);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("gui.sre.role_rotation.selection_timeout",
                            Component.literal(String.valueOf(perPlayerTimeLimit / 20)).withStyle(ChatFormatting.RED),
                            RoleUtils.getRoleName(randomRole).withColor(randomRole.getColor()))
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);
        }

        currentRotationIndex++;
        advanceToNextPlayer(world);
    }

    // ---------- 离线处理 ----------
    public boolean handleOfflinePlayers(ServerLevel world) {
        List<UUID> offlineUnselected = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : playerOrder.entrySet()) {
            UUID uuid = entry.getKey();
            if (!selectedRoles.containsKey(uuid)) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.isRemoved()) {
                    offlineUnselected.add(uuid);
                }
            }
        }
        if (offlineUnselected.isEmpty()) return false;

        for (UUID uuid : offlineUnselected) {
            SRERole randomRole = selectRandomRole();
            selectedRoles.put(uuid, randomRole);
            rolePool.remove(randomRole);
            randomChoosers.add(uuid);
        }

        return true;
    }

    // ---------- 职业调整 ----------
    public void adjustRemainingRoles(ServerLevel serverWorld) {
        ArrayList<SRERole> remainingPool = new ArrayList<>(rolePool);

        ArrayList<SRERole> remainingKillers = new ArrayList<>();
        ArrayList<SRERole> remainingVigilantes = new ArrayList<>();
        ArrayList<SRERole> remainingNeutrals = new ArrayList<>();
        ArrayList<SRERole> remainingSpecialCivilians = new ArrayList<>();

        for (SRERole role : remainingPool) {
            int roleType = PlayerRoleWeightManager.getRoleType(role);
            if (roleType == 4) remainingKillers.add(role);
            else if (roleType == 5) remainingVigilantes.add(role);
            else if (roleType == 2 || roleType == 3) remainingNeutrals.add(role);
            else if (isSpecialCivilianRole(role)) remainingSpecialCivilians.add(role);
        }

        int selectedKillers = 0, selectedVigilantes = 0, selectedNeutrals = 0, selectedSpecialCivilians = 0;
        for (ServerPlayer player : serverWorld.players()) {
            SRERole role = selectedRoles.get(player.getUUID());
            if (role != null) {
                int roleType = PlayerRoleWeightManager.getRoleType(role);
                if (roleType == 4) selectedKillers++;
                else if (roleType == 5) selectedVigilantes++;
                else if (roleType == 2 || roleType == 3) selectedNeutrals++;
                else if (isSpecialCivilianRole(role)) selectedSpecialCivilians++;
            }
        }

        int targetKillers = Math.max(1, RoleCountManager.getKillerCount(totalPlayers));
        int targetVigilantes = Math.max(0, RoleCountManager.getVigilanteCount(totalPlayers));
        int targetNeutrals = Math.max(0, RoleCountManager.getNeutralCount(totalPlayers));

        int neededKillers = Math.max(0, targetKillers - selectedKillers);
        int neededVigilantes = Math.max(0, targetVigilantes - selectedVigilantes);
        int neededNeutrals = Math.max(0, targetNeutrals - selectedNeutrals);
        int neededSpecialCivilians = remainingSpecialCivilians.size();

        int totalNeeded = neededKillers + neededVigilantes + neededNeutrals + neededSpecialCivilians;
        if (totalNeeded <= 0) return;

        List<ServerPlayer> civilianPlayers = new ArrayList<>();
        for (ServerPlayer player : serverWorld.players()) {
            UUID uuid = player.getUUID();
            SRERole role = selectedRoles.get(uuid);
            if (role != null && role.isInnocent() && !role.canUseKiller() && !role.isVigilanteTeam()
                    && !role.isNeutrals() && !isSpecialCivilianRole(role)) {
                civilianPlayers.add(player);
            }
        }

        if (civilianPlayers.isEmpty()) return;

        Random random = new Random(serverWorld.getGameTime());

        ArrayList<SRERole> toAssign = new ArrayList<>();
        for (int i = 0; i < neededKillers && i < remainingKillers.size(); i++)
            toAssign.add(remainingKillers.get(i));
        for (int i = 0; i < neededVigilantes && i < remainingVigilantes.size(); i++)
            toAssign.add(remainingVigilantes.get(i));
        for (int i = 0; i < neededNeutrals && i < remainingNeutrals.size(); i++)
            toAssign.add(remainingNeutrals.get(i));
        for (int i = 0; i < neededSpecialCivilians && i < remainingSpecialCivilians.size(); i++)
            toAssign.add(remainingSpecialCivilians.get(i));

        for (SRERole priorityRole : toAssign) {
            if (civilianPlayers.isEmpty()) break;

            ServerPlayer targetPlayer = civilianPlayers.remove(random.nextInt(civilianPlayers.size()));
            UUID targetUuid = targetPlayer.getUUID();

            SRERole oldRole = selectedRoles.get(targetUuid);
            if (oldRole != null && !isRoleInPool(oldRole)) {
                rolePool.add(oldRole);
            }

            selectedRoles.put(targetUuid, priorityRole);
            rolePool.remove(priorityRole);

            targetPlayer.displayClientMessage(
                    Component.translatable("gui.sre.role_rotation.role_adjusted",
                            RoleUtils.getRoleName(priorityRole).withColor(priorityRole.getColor()))
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
    }

    private boolean isRoleInPool(SRERole role) {
        if (role == null) return false;
        ResourceLocation id = role.identifier();
        for (SRERole r : rolePool) {
            if (id.equals(r.identifier())) return true;
        }
        return false;
    }

    private boolean isSpecialCivilianRole(SRERole role) {
        if (role == null) return false;
        ResourceLocation id = role.identifier();
        for (SRERole special : SPECIAL_CIVILIAN_ROLES) {
            if (id.equals(special.identifier())) return true;
        }
        if (role.isInnocent() && !role.canBeRandomed()) return true;
        return false;
    }

    // ---------- 辅助方法（供 GameMode 使用）----------
    public ArrayList<SRERole> getCurrentCandidates() {
        return currentCandidates;
    }

    public Map<UUID, List<String>> getRoundCandidatesAsStrings() {
        if (currentCandidates.isEmpty()) return Collections.emptyMap();
        UUID currentPlayer = findPlayerByRotationIndex(currentRotationIndex);
        if (currentPlayer == null) return Collections.emptyMap();
        Map<UUID, List<String>> result = new HashMap<>();
        result.put(currentPlayer, currentCandidates.stream()
                .map(r -> r.identifier().toString()).toList());
        return result;
    }

    public Map<UUID, String> getSelectedRolesAsStrings() {
        return selectedRoles.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().identifier().toString()));
    }

    public int getPlayerIndex(UUID uuid) {
        return playerOrder.getOrDefault(uuid, -1);
    }
}
