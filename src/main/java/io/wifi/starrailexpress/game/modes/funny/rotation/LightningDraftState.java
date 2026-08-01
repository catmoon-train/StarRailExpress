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
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.exmo.sre.repair.role.RepairRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class LightningDraftState {
    public static HashMap<UUID, Integer> PLAYER_SORT_WEIGHT = new HashMap<>();
    public static final Random random = new Random();
    public static final int PLAYER_SELECT_COUNT = 3;
    public final List<ServerPlayer> allPlayers;
    public final int totalPlayers;
    // 锁定本轮已展示给玩家的具体角色实例（按UUID），防止随机选择时抢走
    private final Set<UUID> lockedInstanceIds = new HashSet<>();

    // ===== 职业池与结果 =====
    public final Set<SRERole> canReplaceRole = new LinkedHashSet<>(); // 可替换职业类型集合（去重）
    public final ArrayList<RoleInstance> rolePool = new ArrayList<>(); // 当前未被选走的角色实例池
    public final Map<UUID, SRERole> selectedRoles = new LinkedHashMap<>();
    public final Set<UUID> randomChoosers = new HashSet<>();

    // ===== 玩家顺序（按阵营权重降序，同权重随机）=====
    public final List<UUID> playerOrder = new ArrayList<>();

    // ===== 轮次控制 =====
    public int remainingPlayerCount;
    public int currentRoundIndex = 0;
    public int playersInThisRound = 0;
    public Map<UUID, List<RoleInstance>> roundCandidates = new HashMap<>();
    public long roundStartTime;
    public int perPlayerTimeLimit;
    public boolean isSelecting = false;
    public int confirmCountdown = -1;

    // ===== 卡片追踪 =====
    private final Map<Integer, Integer> cardUsedCount = new HashMap<>();
    private final Map<Integer, Integer> cardMaxPerType = new HashMap<>();
    private final Set<UUID> cardReturnedPlayers = new HashSet<>();

    public LightningDraftState(List<ServerPlayer> players) {
        this.allPlayers = new ArrayList<>(players);
        this.totalPlayers = players.size();
        this.remainingPlayerCount = totalPlayers;
    }

    /**
     * 处理已离线的、尚未选择职业的玩家。
     */
    public boolean handleOfflinePlayers(ServerLevel world) {
        List<UUID> offlineUnselected = new ArrayList<>();
        for (UUID uuid : playerOrder) {
            if (!selectedRoles.containsKey(uuid)) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.isRemoved()) {
                    offlineUnselected.add(uuid);
                }
            }
        }
        if (offlineUnselected.isEmpty())
            return false;

        for (UUID uuid : offlineUnselected) {
            List<RoleInstance> oldCandidates = roundCandidates.remove(uuid);
            if (oldCandidates != null) {
                oldCandidates.forEach(ri -> lockedInstanceIds.remove(ri.uuid()));
            }
            RoleInstance randomInstance = selectRandomRole(world);
            selectedRoles.put(uuid, randomInstance.role());
            rolePool.remove(randomInstance);
            remainingPlayerCount--;
            randomChoosers.add(uuid);
        }

        if (isSelecting && roundCandidates.isEmpty()) {
            finishRound(world);
        }
        return true;
    }

    // ---------- 初始化角色池 ----------
    public void initializeRolePool(ServerLevel world) {
        rolePool.clear();
        canReplaceRole.clear();

        int killerCount = Math.max(1, RoleCountManager.getKillerCount(totalPlayers));
        int vigilanteCount = Math.max(0, RoleCountManager.getVigilanteCount(totalPlayers));
        int neutralsCount = Math.max(0, RoleCountManager.getNeutralCount(totalPlayers));

        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;

        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        role.canUseKiller() && !role.isNeutrals() && !role.isNeutralForKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole));
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
        civilianPool.ignoreeRoleOccupiedCount = true;

        // 强制职业直接作为实例加入池子
        int forceRoleCount = 0;
        for (var flip : Harpymodloader.FORCED_MODDED_ROLE_FLIP.entrySet()) {
            var role = flip.getValue();
            if (role == null)
                continue;
            rolePool.add(new RoleInstance(UUID.randomUUID(), role));
            forceRoleCount++;
            switch (role.getRoleType()) {
                case 1:
                    break;
                case 2:
                case 3:
                    neutralsCount--;
                    break;
                case 4:
                    killerCount--;
                    break;
                case 5:
                    vigilanteCount--;
                    break;
            }
        }

        List<RoleInstance> baseRoles = SREMurderGameMode.getAllRoles(
                killerCount, vigilanteCount, neutralsCount,
                totalPlayers, forceRoleCount,
                killerPool, neutralsPool, vigilantePool, civilianPool, true);

        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null) {
                rolePool.add(inst); // 保留原有UUID
            }
        }

        // 生成可替换职业列表
        List<SRERole> replaceableTypes = new ArrayList<>(civilianPool.selectRoles(PLAYER_SELECT_COUNT - 1,
                role -> role.canBeRandomed()
                        && role.opposingRoles.isEmpty() && !isSpecialInnocent(role)));
        int size = replaceableTypes.size();
        for (int i = 0; i < PLAYER_SELECT_COUNT - 1 - size; i++) {
            replaceableTypes.add(TMMRoles.CIVILIAN);
        }
        for (SRERole r : replaceableTypes) {
            canReplaceRole.add(r); // 记录可替换的类型
            rolePool.add(new RoleInstance(UUID.randomUUID(), r)); // 为每个类型创建一个实例
        }

        SRE.LOGGER.info("Replaceable role size {}", replaceableTypes.size());
        for (var r : replaceableTypes) {
            SRE.LOGGER.info("Replaceable Role {}", r.getName().getString());
        }

        initializeCardTracking();
    }

    private void initializeCardTracking() {
        cardUsedCount.clear();
        cardMaxPerType.clear();
        cardReturnedPlayers.clear();
        int limit = Math.max(1, totalPlayers / 10);
        cardMaxPerType.put(4, limit);
        cardMaxPerType.put(2, limit);

        Map<Integer, List<UUID>> byType = new HashMap<>();
        for (ServerPlayer p : allPlayers) {
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(p.getUUID());
            if (forcedType != null) {
                int normalized = normalizeCardType(forcedType);
                byType.computeIfAbsent(normalized, k -> new ArrayList<>()).add(p.getUUID());
            }
        }
        for (Map.Entry<Integer, List<UUID>> entry : byType.entrySet()) {
            int type = entry.getKey();
            List<UUID> uuids = entry.getValue();
            int max = cardMaxPerType.getOrDefault(type, uuids.size());
            cardUsedCount.put(type, Math.min(uuids.size(), max));
            for (int i = max; i < uuids.size(); i++) {
                UUID uid = uuids.get(i);
                PlayerRoleWeightManager.ForcePlayerTeam.remove(uid);
                cardReturnedPlayers.add(uid);
                ServerPlayer sp = allPlayers.stream().filter(p -> p.getUUID().equals(uid)).findFirst().orElse(null);
                if (sp != null) {
                    FactionCardType cardType = FactionCardType.fromRoleType(type);
                    if (cardType != FactionCardType.NONE) {
                        ProgressionDataManager.addFactionCard(sp, cardType, 1);
                        sp.displayClientMessage(Component.translatable("message.sre.role_rotation.card_limit")
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }

    private boolean roleMatchesFaction(SRERole role, int type) {
        return role != null && normalizeCardType(role.getRoleType()) == type;
    }

    private static int normalizeCardType(int rawType) {
        return switch (rawType) {
            case 5 -> 1;
            case 3 -> 2;
            default -> rawType;
        };
    }

    // ---------- 玩家顺序 ----------
    public void assignRotationOrder() {
        List<ServerPlayer> sorted = new ArrayList<>(allPlayers);
        Collections.shuffle(sorted);
        sorted.sort((a, b) -> {
            int aW = PLAYER_SORT_WEIGHT.getOrDefault(a.getUUID(), 0);
            int bW = PLAYER_SORT_WEIGHT.getOrDefault(b.getUUID(), 0);
            if (aW != bW)
                return -Integer.compare(aW, bW);

            boolean a_force = Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(a.getUUID());
            boolean b_force = Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(b.getUUID());
            int a_team = normalizeForceRoleSortType(
                    PlayerRoleWeightManager.ForcePlayerTeam.getOrDefault(a.getUUID(), 0));
            int b_team = normalizeForceRoleSortType(
                    PlayerRoleWeightManager.ForcePlayerTeam.getOrDefault(b.getUUID(), 0));
            if (a_force && b_force)
                return 0;
            if (a_force)
                return -1;
            if (b_force)
                return 1;
            return -Integer.compare(a_team, b_team);
        });
        playerOrder.clear();
        for (ServerPlayer p : sorted) {
            playerOrder.add(p.getUUID());
        }
        PLAYER_SORT_WEIGHT.clear();
    }

    private static int normalizeForceRoleSortType(int type) {
        if (type == 1)
            type = -2;
        if (type == 5)
            type = 0;
        return type;
    }

    // ---------- 轮次计算 ----------
    public void startNextRound(ServerLevel world) {
        if (remainingPlayerCount <= 0) {
            adjustRoles(world);
            startConfirmCountdown();
            return;
        }
        if (rolePool.isEmpty()) {
            for (UUID uuid : playerOrder) {
                if (!selectedRoles.containsKey(uuid)) {
                    selectedRoles.put(uuid, TMMRoles.CIVILIAN);
                    remainingPlayerCount--;
                }
            }
            adjustRoles(world);
            startConfirmCountdown();
            return;
        }
        roundCandidates.clear();
        lockedInstanceIds.clear();

        int b = Math.max(1, rolePool.size() / PLAYER_SELECT_COUNT);
        playersInThisRound = b;

        // 本轮参选玩家
        List<UUID> roundPlayers = new ArrayList<>();
        for (UUID uuid : playerOrder) {
            if (!selectedRoles.containsKey(uuid)) {
                roundPlayers.add(uuid);
                if (roundPlayers.size() >= playersInThisRound)
                    break;
            }
        }

        int need = Math.min(rolePool.size(), playersInThisRound * PLAYER_SELECT_COUNT);
        List<RoleInstance> drawn = new ArrayList<>(rolePool);
        Collections.shuffle(drawn, random);
        drawn = new ArrayList<>(drawn.subList(0, need));

        List<RoleInstance> remainingDrawn = new ArrayList<>(drawn); // 可分配池
        Map<UUID, List<RoleInstance>> candidateMap = new LinkedHashMap<>();
        for (UUID id : roundPlayers) {
            candidateMap.put(id, new ArrayList<>());
        }

        // 1. 强制职业预分配
        for (UUID playerId : roundPlayers) {
            SRERole forcedRole = Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(playerId);
            if (forcedRole != null) {
                Optional<RoleInstance> match = remainingDrawn.stream()
                        .filter(ri -> ri.role() == forcedRole).findFirst();
                match.ifPresent(ri -> {
                    candidateMap.get(playerId).add(ri);
                    remainingDrawn.remove(ri);
                });
            }
        }

        // 2. 强制阵营预分配
        for (UUID playerId : roundPlayers) {
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(playerId);
            if (forcedType == null || forcedType < 1 || forcedType > 5)
                continue;
            int type = normalizeCardType(forcedType);
            if (candidateMap.get(playerId).size() >= PLAYER_SELECT_COUNT)
                continue;

            Optional<RoleInstance> match = remainingDrawn.stream()
                    .filter(ri -> roleMatchesFaction(ri.role(), type)).findFirst();
            if (match.isPresent()) {
                RoleInstance ri = match.get();
                candidateMap.get(playerId).add(ri);
                remainingDrawn.remove(ri);
            } else {
                // 无法提供匹配职业，移除强制要求，退还卡片
                PlayerRoleWeightManager.ForcePlayerTeam.remove(playerId);
                ServerPlayer sp = world.getServer().getPlayerList().getPlayer(playerId);
                if (sp != null) {
                    FactionCardType cardType = FactionCardType.fromRoleType(type);
                    if (cardType != FactionCardType.NONE) {
                        ProgressionDataManager.addFactionCard(sp, cardType, 1);
                        sp.displayClientMessage(Component.translatable("message.sre.role_rotation.card_limit")
                                .withStyle(ChatFormatting.RED), false);
                    }
                }
            }
        }

        // 3. 剩余实例依次分配给玩家，每人最多 PLAYER_SELECT_COUNT 个
        Iterator<RoleInstance> iter = remainingDrawn.iterator();
        for (UUID playerId : roundPlayers) {
            List<RoleInstance> candidates = candidateMap.get(playerId);
            while (candidates.size() < PLAYER_SELECT_COUNT && iter.hasNext()) {
                candidates.add(iter.next());
                iter.remove();
            }
        }

        // 过滤出非空候选列表，并记录锁定实例
        for (Map.Entry<UUID, List<RoleInstance>> entry : candidateMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                roundCandidates.put(entry.getKey(), entry.getValue());
                entry.getValue().forEach(ri -> lockedInstanceIds.add(ri.uuid()));
            }
        }

        currentRoundIndex++;
        roundStartTime = world.getGameTime();

        int maxCandidates = roundCandidates.values().stream().mapToInt(List::size).max().orElse(0);
        int baseTime = maxCandidates * SREConfig.instance().roleRotationPerPlayerPerRoleTime * 20;
        perPlayerTimeLimit = currentRoundIndex == 1 ? baseTime + 60 : baseTime;

        isSelecting = true;
        confirmCountdown = -1;
    }

    // ---------- 处理玩家选择 ----------
    public boolean processSelection(ServerLevel world, UUID playerUuid, int choiceIndex) {
        if (!isSelecting || !roundCandidates.containsKey(playerUuid))
            return false;

        List<RoleInstance> candidates = roundCandidates.remove(playerUuid);
        candidates.forEach(ri -> lockedInstanceIds.remove(ri.uuid()));

        RoleInstance chosen = null;
        if (choiceIndex >= 0 && choiceIndex < candidates.size()) {
            chosen = candidates.get(choiceIndex);
        }
        if (chosen == null || choiceIndex == PLAYER_SELECT_COUNT) { // 随机
            chosen = selectRandomRole(world);
            randomChoosers.add(playerUuid);
        }

        if (chosen == null)
            return false;

        selectedRoles.put(playerUuid, chosen.role());
        rolePool.remove(chosen);
        remainingPlayerCount--;

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            if (canReplaceRole.contains(chosen.role())) {
                player.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selected_adjust",
                                RoleUtils.getRoleName(chosen.role()).withColor(chosen.role().getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selected",
                                RoleUtils.getRoleName(chosen.role()).withColor(chosen.role().getColor()))
                                .withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
        RoleUtils.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);

        if (roundCandidates.isEmpty()) {
            finishRound(world);
        }
        return true;
    }

    private void finishRound(ServerLevel world) {
        isSelecting = false;
        lockedInstanceIds.clear();
        if (remainingPlayerCount > 0) {
            for (ServerPlayer p : world.players()) {
                RoleUtils.playSound(p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1.0f, 1.5f);
            }
            startNextRound(world);
        } else {
            for (ServerPlayer p : world.players()) {
                RoleUtils.playSound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
            }
            adjustRoles(world);
            startConfirmCountdown();
        }
    }

    private void startConfirmCountdown() {
        isSelecting = false;
        confirmCountdown = 6 * 20;
    }

    private RoleInstance selectRandomRole(ServerLevel world) {
        List<RoleInstance> available = rolePool.stream()
                .filter(ri -> !lockedInstanceIds.contains(ri.uuid()))
                .collect(Collectors.toList());
        if (available.isEmpty()) {
            // 极端情况：从整个池子随机，若池子为空则生成一个平民实例
            RoleInstance civilian = new RoleInstance(UUID.randomUUID(), TMMRoles.CIVILIAN);
            rolePool.add(civilian);
            SRE.LOGGER.warn("No available role for random selection, assigning civilian.");
            return civilian;
        }
        return available.get(random.nextInt(available.size()));
    }

    public void timeoutUnfinishedPlayers(ServerLevel world) {
        if (!isSelecting)
            return;
        List<UUID> unfinished = new ArrayList<>(roundCandidates.keySet());
        for (UUID uuid : unfinished) {
            List<RoleInstance> oldCandidates = roundCandidates.remove(uuid);
            if (oldCandidates != null) {
                oldCandidates.forEach(ri -> lockedInstanceIds.remove(ri.uuid()));
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            RoleInstance randomInstance = selectRandomRole(world);
            selectedRoles.put(uuid, randomInstance.role());
            rolePool.remove(randomInstance);
            remainingPlayerCount--;
            randomChoosers.add(uuid);
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("gui.sre.role_rotation.selection_timeout_noargs")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }
        finishRound(world);
    }

    public void adjustRoles(ServerLevel serverWorld) {
        var canReplacePlayers = new ArrayList<UUID>();
        for (Entry<UUID, SRERole> entrySet : selectedRoles.entrySet()) {
            if (canReplaceRole.contains(entrySet.getValue()) || entrySet.getValue().equals(TMMRoles.CIVILIAN)) {
                canReplacePlayers.addFirst(entrySet.getKey());
            }
        }
        var needToReplaceRole = new ArrayList<SRERole>();
        boolean roleRotationForceRoleSettings = SREConfig.instance().roleRotationForceRoleSettings;
        for (RoleInstance ri : rolePool) {
            SRERole role = ri.role();
            if (canReplaceRole.contains(role) || role.equals(TMMRoles.CIVILIAN)) {
                continue;
            }
            if (role.isInnocent() && !isSpecialInnocent(role) && !roleRotationForceRoleSettings) {
                continue;
            }
            needToReplaceRole.add(role);
        }
        if (needToReplaceRole.isEmpty())
            return;

        Collections.shuffle(needToReplaceRole);
        int t = 0;
        for (SRERole r : needToReplaceRole) {
            if (canReplacePlayers.isEmpty()) {
                SRE.LOGGER.error("Need {} more innocent player.", needToReplaceRole.size() - t);
                break;
            }
            var p = canReplacePlayers.getFirst();
            canReplacePlayers.removeFirst();
            var old = selectedRoles.getOrDefault(p, SpecialGameModeRoles.CUSTOM_PENDING);
            selectedRoles.put(p, r);
            var pp = serverWorld.getPlayerByUUID(p);
            SRE.LOGGER.info("Replace {}'s old role {} with new role {}",
                    pp == null ? "null" : pp.getName().getString(), old.getName().getString(),
                    r.getName().getString());
            t++;
        }
    }

    private static boolean isSpecialInnocent(SRERole role) {
        if (!role.occupationRoles.isEmpty())
            return true;
        if (!role.occupationedRoles.isEmpty())
            return true;
        return false;
    }

    public Map<UUID, List<String>> getRoundCandidatesAsStrings() {
        return roundCandidates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(ri -> ri.role().identifier().toString()).toList()));
    }

    public Map<UUID, String> getSelectedRolesAsStrings() {
        return selectedRoles.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().identifier().toString()));
    }
}