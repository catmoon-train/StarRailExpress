package io.wifi.starrailexpress.game.modes.funny.rotation;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import net.exmo.sre.repair.role.RepairRole;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class LightningDraftState {

    public final List<ServerPlayer> allPlayers;
    public final int totalPlayers;

    // 锁定本轮分配给玩家的候选职业，防止随机选择抢走
    private final Set<SRERole> lockedCandidates = new HashSet<>();

    // ===== 职业池与结果 =====
    public final ArrayList<SRERole> canReplaceRole = new ArrayList<>();
    public final ArrayList<SRERole> rolePool = new ArrayList<>();
    public final Map<UUID, SRERole> selectedRoles = new LinkedHashMap<>();
    public final Set<UUID> randomChoosers = new HashSet<>();

    // ===== 玩家顺序（按阵营权重降序，同权重随机）=====
    public final List<UUID> playerOrder = new ArrayList<>();

    // ===== 轮次控制 =====
    public int remainingRoles;
    public int currentRoundIndex = 0;
    public int playersInThisRound = 0;
    public Map<UUID, List<SRERole>> roundCandidates = new HashMap<>();
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
        this.remainingRoles = totalPlayers;
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
            roundCandidates.remove(uuid); // 从本轮候选移除
            SRERole randomRole = selectRandomRole(world);
            selectedRoles.put(uuid, randomRole);
            rolePool.remove(randomRole);
            remainingRoles--;
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
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        !RoleUtils.compareRole(role, ModRoles.PUPPETEER) &&
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
        // 职业池总数 = 总玩家数
        // 最后几人不允许选
        List<RoleInstance> baseRoles = SREMurderGameMode.getAllRoles(
                killerCount, vigilanteCount, neutralsCount,
                totalPlayers, 0,
                killerPool, neutralsPool, vigilantePool, civilianPool, true);

        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null) {
                rolePool.add(inst.role());
            }
        }
        canReplaceRole.addAll(civilianPool.selectRoles(2));
        rolePool.addAll(canReplaceRole);
        initializeCardTracking();
    }

    private void initializeCardTracking() {
        cardUsedCount.clear();
        cardMaxPerType.clear();
        cardReturnedPlayers.clear();
        int limit = Math.max(1, totalPlayers / 7);
        cardMaxPerType.put(4, limit);
        cardMaxPerType.put(2, limit);
        cardMaxPerType.put(1, limit);

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
            int max = cardMaxPerType.getOrDefault(type, 0);
            cardUsedCount.put(type, Math.min(uuids.size(), max));
            for (int i = max; i < uuids.size(); i++) {
                UUID uid = uuids.get(i);
                PlayerRoleWeightManager.ForcePlayerTeam.remove(uid);
                cardReturnedPlayers.add(uid);
                ServerPlayer sp = allPlayers.stream().filter(p -> p.getUUID().equals(uid)).findFirst().orElse(null);
                if (sp != null) {
                    FactionCardType cardType = FactionCardType.fromInt(type);
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
        return role == null ? false : (normalizeCardType(role.getRoleType()) == type);
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
        playerOrder.clear();
        for (ServerPlayer p : sorted) {
            playerOrder.add(p.getUUID());
        }
    }

    // ---------- 轮次计算 ----------
    public void startNextRound(ServerLevel world) {
        if (remainingRoles <= 0) {
            adjustRoles(world);
            startConfirmCountdown();
            return;
        }

        int n = remainingRoles;
        int b = Math.max(1, n / 3);
        playersInThisRound = b;

        List<UUID> roundPlayers = new ArrayList<>();
        for (UUID uuid : playerOrder) {
            if (!selectedRoles.containsKey(uuid)) {
                roundPlayers.add(uuid);
                if (roundPlayers.size() == b)
                    break;
            }
        }

        int need = Math.min(rolePool.size(), playersInThisRound * 3);
        List<SRERole> drawn = new ArrayList<>(rolePool);
        Collections.shuffle(drawn, new Random(world.getGameTime()));
        drawn = new ArrayList<>(drawn.subList(0, need));

        // 预分配：为有强制阵营的玩家准备一个匹配职业
        Map<UUID, SRERole> preAssigned = new LinkedHashMap<>();
        Set<SRERole> usedInThisRound = new HashSet<>();
        for (UUID playerId : roundPlayers) {
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(playerId);
            if (forcedType == null || forcedType < 1 || forcedType > 5)
                continue;

            int type = normalizeCardType(forcedType);
            // 尝试从剩余职业池中寻找匹配职业（排除已锁定和本轮已占用的）
            SRERole match = null;
            for (SRERole role : rolePool) {
                if (!lockedCandidates.contains(role) && !usedInThisRound.contains(role)
                        && roleMatchesFaction(role, type)) {
                    match = role;
                    break;
                }
            }

            if (match != null) {
                preAssigned.put(playerId, match);
                usedInThisRound.add(match);
            } else {
                // 无法提供匹配职业，移除强制要求，退还卡片
                PlayerRoleWeightManager.ForcePlayerTeam.remove(playerId);
                ServerPlayer sp = world.getServer().getPlayerList().getPlayer(playerId);
                if (sp != null) {
                    FactionCardType cardType = FactionCardType.fromInt(type);
                    if (cardType != FactionCardType.NONE) {
                        ProgressionDataManager.addFactionCard(sp, cardType, 1);
                        sp.displayClientMessage(Component.translatable("message.sre.role_rotation.faction_fallback")
                                .withStyle(ChatFormatting.RED), false);
                    }
                }
            }
        }
        drawn.removeAll(usedInThisRound);
        roundCandidates.clear();
        lockedCandidates.clear(); // 清空锁定集
        int idx = 0;
        for (UUID playerId : roundPlayers) {
            var preRole = preAssigned.getOrDefault(playerId, null);
            int count = Math.min(3, need - idx);
            if (preRole != null) {
                count--;
            }
            if (count <= 0 && preRole == null)
                break;
            List<SRERole> candidates = new ArrayList<>();
            if (count > 0) {
                candidates.addAll(drawn.subList(idx, idx + count));
            }
            if (preRole != null) {
                candidates.add(preRole);
            }
            roundCandidates.put(playerId, candidates);
            lockedCandidates.addAll(candidates); // 锁定本轮候选
            idx += count;
        }

        currentRoundIndex++;
        roundStartTime = world.getGameTime();

        int baseTime = roundCandidates.values().stream()
                .mapToInt(List::size).max().orElse(0) * 3 * 20;
        if (currentRoundIndex == 1) {
            perPlayerTimeLimit = baseTime + 60; // 第一轮多3秒缓冲
        } else {
            perPlayerTimeLimit = baseTime;
        }

        isSelecting = true;
        confirmCountdown = -1;
    }

    // ---------- 处理玩家选择 ----------
    public boolean processSelection(ServerLevel world, UUID playerUuid, int choiceIndex) {
        if (!isSelecting || !roundCandidates.containsKey(playerUuid))
            return false;

        List<SRERole> candidates = roundCandidates.get(playerUuid);
        SRERole chosen = null;
        for (var i = 0; i < candidates.size(); i++) {
            lockedCandidates.remove(candidates.get(i)); // 从锁定集移除
        }
        if (choiceIndex >= 0 && choiceIndex < candidates.size()) {
            chosen = candidates.get(choiceIndex);
        } else if (choiceIndex == 3) { // 随机
            chosen = selectRandomRole(world); // 排除锁定职业
            randomChoosers.add(playerUuid);
        }
        if (chosen == null)
            return false;

        selectedRoles.put(playerUuid, chosen);
        rolePool.remove(chosen);
        remainingRoles--;

        roundCandidates.remove(playerUuid);

        ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("gui.sre.role_rotation.selected",
                            RoleUtils.getRoleName(chosen).withColor(chosen.getColor()))
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
        world.playSound(null, player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);

        if (roundCandidates.isEmpty()) {
            finishRound(world);
        }
        return true;
    }

    private void finishRound(ServerLevel world) {
        isSelecting = false;
        lockedCandidates.clear();
        if (remainingRoles > 0) {
            // 一轮结束，新轮提示音
            for (ServerPlayer p : world.players()) {
                world.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.NOTE_BLOCK_BELL, SoundSource.MASTER, 1.0f, 1.5f);
            }
            startNextRound(world);
        } else {
            // 全部结束提示音
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

    private SRERole selectRandomRole(ServerLevel world) {
        List<SRERole> available = new ArrayList<>(rolePool);
        available.removeAll(lockedCandidates);
        if (available.isEmpty())
            return TMMRoles.CIVILIAN;
        return available.get(new Random(world.getGameTime()).nextInt(available.size()));
    }

    public void timeoutUnfinishedPlayers(ServerLevel world) {
        if (!isSelecting)
            return;
        List<UUID> unfinished = new ArrayList<>(roundCandidates.keySet());
        for (UUID uuid : unfinished) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                SRERole randomRole = selectRandomRole(world);
                selectedRoles.put(uuid, randomRole);
                rolePool.remove(randomRole);
                remainingRoles--;
                randomChoosers.add(uuid);
                player.displayClientMessage(
                        Component.literal("选择超时，已随机分配职业").withStyle(ChatFormatting.RED),
                        true);
            }
            List<SRERole> oldCandidates = roundCandidates.remove(uuid);
            if (oldCandidates != null)
                lockedCandidates.removeAll(oldCandidates);
        }
        finishRound(world);
    }

    public void adjustRoles(ServerLevel serverWorld) {
        // 不做任何替换
        var canReplacePlayers = new ArrayList<UUID>();
        for (Entry<UUID, SRERole> entrySet : selectedRoles.entrySet()) {
            if (canReplaceRole.contains(entrySet.getValue())) {
                canReplacePlayers.addFirst(entrySet.getKey());
            }
        }
        var needToReplaceRole = new ArrayList<SRERole>();
        boolean roleRotationForceRoleSettings = SREConfig.instance().roleRotationForceRoleSettings;
        for (SRERole role : rolePool) {
            if (canReplaceRole.contains(role)) {
                continue;
            }
            if (!roleRotationForceRoleSettings && role.isInnocent()) {
                continue;
            }
            needToReplaceRole.add(role);
        }
        if (needToReplaceRole.isEmpty())
            return;
        Collections.shuffle(needToReplaceRole);
        int t = 0;
        for (var r : needToReplaceRole) {
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
                    pp == null ? "null" : pp.getName().getString(), p, old.getName().getString(),
                    r.getName().getString());
            t++;
        }
    }

    public Map<UUID, List<String>> getRoundCandidatesAsStrings() {
        return roundCandidates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(r -> r.identifier().toString()).toList()));
    }

    public Map<UUID, String> getSelectedRolesAsStrings() {
        return selectedRoles.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().identifier().toString()));
    }
}