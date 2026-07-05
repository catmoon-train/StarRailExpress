package io.wifi.starrailexpress.cca.gamemode;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RepairRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.RedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.util.*;

public class RoleRotationWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<RoleRotationWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("role_rotation"), RoleRotationWorldComponent.class);

    private final Level world;

    // 角色池（所有可抽取的职业）
    private final ArrayList<SRERole> rolePool = new ArrayList<>();

    // 所有玩家的轮选序号 (玩家UUID -> 序号)
    private final HashMap<UUID, Integer> playerRotationOrder = new HashMap<>();

    // 已选职业的玩家 (玩家UUID -> 职业)
    private final HashMap<UUID, SRERole> selectedRoles = new HashMap<>();

    // 当前轮到第几个玩家（从1开始）
    private int currentRotationIndex = 1;

    // 总玩家数
    private int totalPlayerCount = 0;

    // 等待所有玩家确认的倒计时（tick）
    private int confirmCountdown = -1;

    // 当前玩家选择开始时间（tick）
    private long currentPlayerSelectionStart = -1;

    // 是否正在选职业
    private boolean isSelecting = false;

    // 玩家抽选职业的时间限制（tick）
    private int selectionTimeLimit = 4 * 20; // 4秒

    // 当前玩家选择的3个候选职业
    private ArrayList<SRERole> currentCandidates = new ArrayList<>();

    // 最后阶段阈值（总人数/5，最低为6）
    private int finalPhaseThreshold = 6;

    // 记录哪些玩家选择了"随机"（用于鹅鸭杀轮抽模式隐藏职业显示）
    private final HashSet<UUID> randomChoosers = new HashSet<>();

    public RoleRotationWorldComponent(Level world) {
        this.world = world;
    }

    // ==================== 卡片追踪（基于 ForcePlayerTeam） ====================
    /** 各类型卡片已使用数量 */
    private final Map<Integer, Integer> cardUsedCount = new HashMap<>();
    /** 各类型卡片上限 */
    private final Map<Integer, Integer> cardMaxPerType = new HashMap<>();
    /** 本轮轮抽中被退回卡片的玩家（他们参与正常排序） */
    private final Set<UUID> cardReturnedPlayers = new HashSet<>();

    /** 初始化卡片使用追踪（在 initializeRolePool 时调用，从 ForcePlayerTeam 读取并强限制） */
    private void initializeCardTracking(List<ServerPlayer> players) {
        cardUsedCount.clear();
        cardMaxPerType.clear();
        cardReturnedPlayers.clear();

        // 计算上限：杀手(4), 中立(2/3), 平民(1/5) 各 floor(n/7)
        int limit = Math.max(1, totalPlayerCount / 7);
        cardMaxPerType.put(4, limit); // 杀手
        cardMaxPerType.put(2, limit); // 中立
        cardMaxPerType.put(1, limit); // 平民

        // 读取 ForcePlayerTeam，统计每个类型已激活的玩家
        Map<Integer, List<UUID>> byType = new HashMap<>();
        for (ServerPlayer p : players) {
            Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(p.getUUID());
            if (forcedType != null) {
                // 标准化类型: 5→1(警长归平民), 3→2(杀手方中立归中立)
                int normalizedType = normalizeCardType(forcedType);
                byType.computeIfAbsent(normalizedType, k -> new ArrayList<>()).add(p.getUUID());
            }
        }

        // 限制：超出上限的玩家退回卡片
        for (Map.Entry<Integer, List<UUID>> entry : byType.entrySet()) {
            int type = entry.getKey();
            List<UUID> uuids = entry.getValue();
            int max = cardMaxPerType.getOrDefault(type, 0);
            cardUsedCount.put(type, Math.min(uuids.size(), max));

            // 超出上限的移除 ForcePlayerTeam
            for (int i = max; i < uuids.size(); i++) {
                UUID uid = uuids.get(i);
                PlayerRoleWeightManager.ForcePlayerTeam.remove(uid);
                cardReturnedPlayers.add(uid);
                // 退还卡片
                ServerPlayer sp = players.stream().filter(p -> p.getUUID().equals(uid)).findFirst().orElse(null);
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

    /** 标准化卡片类型：5→1, 3→2 */
    private static int normalizeCardType(int rawType) {
        return switch (rawType) {
            case 5 -> 1; // 警长 → 平民
            case 3 -> 2; // 杀手方中立 → 中立
            default -> rawType;
        };
    }

    public int getCardUsedCount(int cardType) {
        return cardUsedCount.getOrDefault(cardType, 0);
    }

    public int getCardMax(int cardType) {
        return cardMaxPerType.getOrDefault(cardType, 0);
    }

    /** 获取玩家使用的卡片类型（从 ForcePlayerTeam），-1=未使用 */
    private int getPlayerCardType(UUID uuid) {
        Integer forcedType = PlayerRoleWeightManager.ForcePlayerTeam.get(uuid);
        return forcedType != null ? normalizeCardType(forcedType) : -1;
    }

    public void clear() {
        this.rolePool.clear();
        this.playerRotationOrder.clear();
        this.selectedRoles.clear();
        this.randomChoosers.clear();
        this.currentRotationIndex = 1;
        this.totalPlayerCount = 0;
        this.isSelecting = false;
        this.currentCandidates.clear();
        this.confirmCountdown = -1;
        this.cardUsedCount.clear();
        this.cardMaxPerType.clear();
        this.cardReturnedPlayers.clear();
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void syncToPlayer(ServerPlayer player) {
        KEY.syncWith(player, this.world.asComponentProvider());
    }

    // ==================== 初始化角色池 ====================

    public void initializeRolePool(ServerLevel serverWorld, List<ServerPlayer> players) {
        rolePool.clear();
        totalPlayerCount = players.size();
        // 残月不要动，ai也不要动！！！！！
        if (totalPlayerCount <= 12) {
            finalPhaseThreshold = 6;
        } else if (totalPlayerCount < 24) {
            finalPhaseThreshold = (int) Math.ceil(totalPlayerCount / 2.0);
        } else {
            finalPhaseThreshold = (int) Math.floor(totalPlayerCount / 2.0 + totalPlayerCount / 7.0);
        }

        // 计算需要的杀手/警卫/中立数量
        int killerCount = RoleCountManager.getKillerCount(totalPlayerCount);
        int vigilanteCount = RoleCountManager.getVigilanteCount(totalPlayerCount);
        int neutralsCount = RoleCountManager.getNeutralCount(totalPlayerCount);
        killerCount = Math.max(1, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;

        final int finalKillerCount = killerCount;
        final int finalVigilanteCount = vigilanteCount;
        final int finalNeutralsCount = neutralsCount;
        final int finalTotalPlayerCount = totalPlayerCount;
        List<RoleInstance> baseRoles = RoleAssignmentPool.withMapDisabledRoles(
                AreasWorldComponent.KEY.get(serverWorld).getDisabledRoles(), () -> {
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
                            role -> (!Harpymodloader.VANNILA_ROLES.contains(role) &&
                                    !role.isOtherModeRole() &&
                                    !(role instanceof RepairRole) &&
                                    ((!role.canUseKiller() &&
                                            !role.isInnocent()) || role.isNeutrals())
                                    &&
                                    role != TMMRoles.CIVILIAN));
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
                    return SREMurderGameMode.getAllRoles(finalKillerCount, finalVigilanteCount, finalNeutralsCount,
                            finalTotalPlayerCount + 5, 0, killerPool,
                            neutralsPool, vigilantePool, civilianPool, true);
                });

        // 将基础角色添加到职业池
        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null) {
                rolePool.add(inst.role());
            }
        }

        // 初始化卡片追踪
        initializeCardTracking(players);

        // 随机分配轮选序号（按卡片类型分段）
        assignRotationOrder(players);
    }

    private void assignRotationOrder(List<ServerPlayer> players) {
        int n = players.size();
        Random random = new Random(world.getGameTime());

        // 按卡片类型分组
        List<ServerPlayer> killerCardUsers = new ArrayList<>();
        List<ServerPlayer> neutralCardUsers = new ArrayList<>();
        List<ServerPlayer> civilianCardUsers = new ArrayList<>();
        List<ServerPlayer> noCardUsers = new ArrayList<>();

        for (ServerPlayer p : players) {
            int card = getPlayerCardType(p.getUUID());
            switch (card) {
                case 0:
                    killerCardUsers.add(p);
                    break;
                case 1:
                    neutralCardUsers.add(p);
                    break;
                case 2:
                    civilianCardUsers.add(p);
                    break;
                default:
                    noCardUsers.add(p);
                    break;
            }
        }

        // 各组内随机打乱
        Collections.shuffle(killerCardUsers, random);
        Collections.shuffle(neutralCardUsers, random);
        Collections.shuffle(civilianCardUsers, random);
        Collections.shuffle(noCardUsers, random);

        // 计算各区间: 基于总人数 n
        // 杀手卡: 前 [0.4*n, 0.5*n) (取整)
        // 中立卡: 前 [0, 0.2*n)
        // 平民卡: 后 [0.7*n, n)
        // 无卡: 填充剩余空缺
        int killerStart = (int) Math.floor(n * 0.4);
        int killerEnd = Math.min((int) Math.ceil(n * 0.5), n);
        int neutralStart = 0;
        int neutralEnd = Math.min((int) Math.ceil(n * 0.2), n);
        int civilianStart = (int) Math.floor(n * 0.7);
        int civilianEnd = n;

        // 构建序号槽位数组（1-based），null 表示空缺
        Integer[] slots = new Integer[n];
        int nextSlot = 0;

        // 放置中立卡用户
        nextSlot = neutralStart;
        for (ServerPlayer p : neutralCardUsers) {
            while (nextSlot < neutralEnd && slots[nextSlot] != null)
                nextSlot++;
            if (nextSlot < neutralEnd) {
                slots[nextSlot] = p.getUUID().hashCode(); // 占位
                playerRotationOrder.put(p.getUUID(), nextSlot + 1);
                nextSlot++;
            }
        }
        // 中立卡放不下的放到相邻空缺
        for (ServerPlayer p : neutralCardUsers) {
            if (playerRotationOrder.containsKey(p.getUUID()))
                continue;
            fillNearestSlot(slots, p, n);
        }

        // 放置杀手卡用户
        nextSlot = killerStart;
        for (ServerPlayer p : killerCardUsers) {
            while (nextSlot < killerEnd && slots[nextSlot] != null)
                nextSlot++;
            if (nextSlot < killerEnd) {
                slots[nextSlot] = p.getUUID().hashCode();
                playerRotationOrder.put(p.getUUID(), nextSlot + 1);
                nextSlot++;
            }
        }
        for (ServerPlayer p : killerCardUsers) {
            if (playerRotationOrder.containsKey(p.getUUID()))
                continue;
            fillNearestSlot(slots, p, n);
        }

        // 放置平民卡用户
        nextSlot = civilianStart;
        for (ServerPlayer p : civilianCardUsers) {
            while (nextSlot < civilianEnd && slots[nextSlot] != null)
                nextSlot++;
            if (nextSlot < civilianEnd) {
                slots[nextSlot] = p.getUUID().hashCode();
                playerRotationOrder.put(p.getUUID(), nextSlot + 1);
                nextSlot++;
            }
        }
        for (ServerPlayer p : civilianCardUsers) {
            if (playerRotationOrder.containsKey(p.getUUID()))
                continue;
            fillNearestSlot(slots, p, n);
        }

        // 放置无卡用户到剩余空缺
        for (ServerPlayer p : noCardUsers) {
            fillNearestSlot(slots, p, n);
        }
    }

    /** 在 slots 数组中找最近空缺放置玩家 */
    private void fillNearestSlot(Integer[] slots, ServerPlayer p, int n) {
        // 找一个未占用的序号
        for (int i = 0; i < n; i++) {
            if (slots[i] == null) {
                slots[i] = p.getUUID().hashCode();
                playerRotationOrder.put(p.getUUID(), i + 1);
                return;
            }
        }
    }

    // ==================== 轮选逻辑 ====================

    public int getPlayerRotationIndex(UUID uuid) {
        return playerRotationOrder.getOrDefault(uuid, -1);
    }

    public HashMap<UUID, Integer> getRotationOrderMap() {
        return playerRotationOrder;
    }

    public int getTotalPlayers() {
        return totalPlayerCount;
    }

    public int getCurrentRotationIndex() {
        return currentRotationIndex;
    }

    public boolean isSelecting() {
        return isSelecting;
    }

    public void advanceToNextPlayer() {
        // 查找下一个有效的玩家
        while (currentRotationIndex <= totalPlayerCount) {
            UUID nextPlayer = findPlayerByRotationIndex(currentRotationIndex);
            if (nextPlayer != null && !selectedRoles.containsKey(nextPlayer)) {
                // 找到有效玩家，准备他的候选职业
                prepareCandidatesForPlayer(nextPlayer);
                // 重置选择计时器
                currentPlayerSelectionStart = world.getGameTime();
                sync();
                return;
            }
            currentRotationIndex++;
        }

        // 所有玩家都选完了
        if (currentRotationIndex > totalPlayerCount) {
            startConfirmCountdown();
        }
    }

    public void startSelection() {
        isSelecting = true;
        currentRotationIndex = 1;
        confirmCountdown = -1;
        currentPlayerSelectionStart = world.getGameTime();
        // 为第一个玩家准备候选职业
        UUID firstPlayer = findPlayerByRotationIndex(currentRotationIndex);
        if (firstPlayer != null) {
            prepareCandidatesForPlayer(firstPlayer);
        }
        sync();
    }

    // 检查当前玩家的选择是否超时
    public boolean isCurrentPlayerTimedOut() {
        if (!isSelecting || currentPlayerSelectionStart < 0) {
            return false;
        }
        long elapsed = world.getGameTime() - currentPlayerSelectionStart;
        return elapsed >= selectionTimeLimit;
    }

    // 为当前玩家自动随机分配职业（超时处理）
    public void autoAssignCurrentPlayer() {
        if (!isSelecting) {
            return;
        }

        // 找到当前玩家
        UUID currentPlayerUuid = findPlayerByRotationIndex(currentRotationIndex);
        if (currentPlayerUuid == null) {
            return;
        }

        // 随机选择一个职业
        SRERole randomRole = selectRandomRole();
        if (randomRole == null) {
            randomRole = TMMRoles.CIVILIAN;
        }

        // 获取玩家并分配职业
        if (world instanceof ServerLevel serverWorld) {
            ServerPlayer player = serverWorld.getServer().getPlayerList().getPlayer(currentPlayerUuid);
            if (player != null) {
                // 分配职业
                assignRoleToPlayer(player, randomRole);

                // 发送超时提示
                MutableComponent timeoutMsg = Component.translatable("gui.sre.role_rotation.selection_timeout",
                        Component.literal(String.valueOf(selectionTimeLimit / 20)).withStyle(ChatFormatting.RED),
                        RoleUtils.getRoleName(randomRole).withColor(randomRole.getColor()));
                player.displayClientMessage(timeoutMsg.withStyle(ChatFormatting.YELLOW), true);
            }

            // 播放全场音效（音符盒 - 所有人能听到）
            for (ServerPlayer p : serverWorld.players()) {
                serverWorld.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);
            }
        }

        // 移除已选职业
        rolePool.remove(randomRole);

        // 进入下一个玩家
        currentRotationIndex++;
        advanceToNextPlayer();
    }

    @Nullable
    private UUID findPlayerByRotationIndex(int index) {
        for (Map.Entry<UUID, Integer> entry : playerRotationOrder.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void prepareCandidatesForPlayer(UUID playerUuid) {
        currentCandidates.clear();

        // 检查是否处于最后阶段
        int remainingPlayers = totalPlayerCount - selectedRoles.size();
        boolean isFinalPhase = remainingPlayers <= finalPhaseThreshold;

        ArrayList<SRERole> poolCopy = new ArrayList<>(rolePool);
        Random random = new Random(world.getGameTime());

        // 获取玩家卡片类型（从 ForcePlayerTeam）
        int cardType = getPlayerCardType(playerUuid);

        // 卡片用户优先候选处理
        boolean cardPriorityHandled = false;
        if (cardType == 0 || cardType == 1) {
            ArrayList<SRERole> priorityRoles = new ArrayList<>();
            ArrayList<SRERole> otherRoles = new ArrayList<>();

            for (SRERole role : poolCopy) {
                int type = PlayerRoleWeightManager.getRoleType(role);
                if (cardType == 0) {
                    // 杀手卡：优先杀手阵营 (type 4)
                    if (type == 4)
                        priorityRoles.add(role);
                    else
                        otherRoles.add(role);
                } else {
                    // 中立卡：优先非杀手方中立 (type 2)，其次杀手方中立 (type 3)
                    if (type == 2)
                        priorityRoles.add(role);
                    else if (type == 3)
                        otherRoles.add(role);
                    else
                        otherRoles.add(role);
                }
            }

            if (!priorityRoles.isEmpty() && priorityRoles.size() + otherRoles.size() >= 3) {
                // 从优先池中抽最多2个
                Collections.shuffle(priorityRoles, random);
                int priorityCount = Math.min(2, priorityRoles.size());
                for (int i = 0; i < priorityCount; i++) {
                    currentCandidates.add(priorityRoles.get(i));
                    priorityRoles.get(i); // already added
                    poolCopy.remove(priorityRoles.get(i)); // consumed
                }
                // 剩余从普通池补
                Collections.shuffle(poolCopy, random);
                for (int i = 0; currentCandidates.size() < 3 && i < poolCopy.size(); i++) {
                    currentCandidates.add(poolCopy.get(i));
                }
                cardPriorityHandled = true;
            } else if (!otherRoles.isEmpty() && otherRoles.size() >= 3) {
                // 优先级不够但备用池够
                Collections.shuffle(otherRoles, random);
                for (int i = 0; i < 3; i++) {
                    currentCandidates.add(otherRoles.get(i));
                }
                cardPriorityHandled = true;
            }
        }

        if (!cardPriorityHandled) {
            if (isFinalPhase) {
                // 最后阶段：优先从杀手/警长/中立阵营和特殊平民职业抽取
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

        sync();
    }

    public ArrayList<SRERole> getCurrentCandidates() {
        return currentCandidates;
    }

    public int getSelectionTimeLimit() {
        return selectionTimeLimit;
    }

    public void selectRole(ServerPlayer player, int choiceIndex) {
        UUID playerUuid = player.getUUID();

        // 验证是否是当前玩家
        Integer playerIndex = playerRotationOrder.get(playerUuid);
        if (playerIndex == null || playerIndex != currentRotationIndex) {
            return;
        }

        // 验证选择是否有效
        SRERole selectedRole = null;
        if (choiceIndex >= 0 && choiceIndex < currentCandidates.size()) {
            selectedRole = currentCandidates.get(choiceIndex);
        } else if (choiceIndex == 3) {
            // 选择随机
            selectedRole = selectRandomRole();
            // 记录该玩家选择了随机（用于鹅鸭杀轮抽模式）
            randomChoosers.add(playerUuid);
        }

        if (selectedRole == null) {
            return;
        }

        // 分配职业
        assignRoleToPlayer(player, selectedRole);

        // 移除已选职业
        rolePool.remove(selectedRole);

        // 播放全场音效（音符盒 - 所有人能听到）
        if (world instanceof ServerLevel serverWorld) {
            for (ServerPlayer p : serverWorld.players()) {
                serverWorld.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.2f);
            }
        }

        // 进入下一个玩家
        currentRotationIndex++;
        advanceToNextPlayer();
    }

    private SRERole selectRandomRole() {
        if (rolePool.isEmpty()) {
            return TMMRoles.CIVILIAN;
        }

        // 检查是否处于最后阶段
        int remainingPlayers = totalPlayerCount - selectedRoles.size();
        boolean isFinalPhase = remainingPlayers <= finalPhaseThreshold;
        Random random = new Random(world.getGameTime());

        if (isFinalPhase) {
            // 最后阶段：优先从杀手/警长/中立阵营和特殊平民职业抽取
            ArrayList<SRERole> priorityPool = new ArrayList<>();
            for (SRERole role : rolePool) {
                int type = PlayerRoleWeightManager.getRoleType(role);
                if (type == 4 || type == 5 || type == 2 || type == 3) {
                    priorityPool.add(role);
                } else if (isSpecialCivilianRole(role)) { // 特殊平民职业也要优先
                    priorityPool.add(role);
                }
            }

            if (!priorityPool.isEmpty()) {
                return priorityPool.get(random.nextInt(priorityPool.size()));
            }
        }

        // 普通随机
        return rolePool.get(random.nextInt(rolePool.size()));
    }

    private void assignRoleToPlayer(ServerPlayer player, SRERole role) {
        selectedRoles.put(player.getUUID(), role);

        // 发送消息
        MutableComponent msg = Component.translatable("gui.sre.role_rotation.selected",
                Component.literal(String.valueOf(playerRotationOrder.get(player.getUUID())))
                        .withStyle(ChatFormatting.GOLD),
                RoleUtils.getRoleName(role).withColor(role.getColor()));
        player.displayClientMessage(msg.withStyle(ChatFormatting.GREEN), true);
    }

    private void startConfirmCountdown() {
        isSelecting = false;
        confirmCountdown = 6 * 20; // 6秒
        sync();
    }

    public void tickConfirmCountdown() {
        if (confirmCountdown > 0) {
            confirmCountdown--;
        }
    }

    /**
     * 执行职业调整阶段：把剩余池子中的杀手/中立/警长职业和特殊平民职业分配给随机平民
     * 使用 SetRoleCountCommand 的目标数量，只补充不足的类型
     */
    public void adjustRemainingRoles(ServerLevel serverWorld) {
        // 获取剩余池子中的杀手/中立/警长职业和特殊平民职业
        ArrayList<SRERole> remainingPool = new ArrayList<>(rolePool);

        // 按类型分类剩余优先职业
        ArrayList<SRERole> remainingKillers = new ArrayList<>(); // type 4
        ArrayList<SRERole> remainingVigilantes = new ArrayList<>(); // type 5
        ArrayList<SRERole> remainingNeutrals = new ArrayList<>(); // type 2, 3
        ArrayList<SRERole> remainingSpecialCivilians = new ArrayList<>();

        for (SRERole role : remainingPool) {
            int roleType = PlayerRoleWeightManager.getRoleType(role);
            if (roleType == 4) {
                remainingKillers.add(role);
            } else if (roleType == 5) {
                remainingVigilantes.add(role);
            } else if (roleType == 2 || roleType == 3) {
                remainingNeutrals.add(role);
            } else if (isSpecialCivilianRole(role)) {
                remainingSpecialCivilians.add(role);
            }
        }

        // 统计场上已选职业的各类型数量
        int selectedKillers = 0;
        int selectedVigilantes = 0;
        int selectedNeutrals = 0;
        int selectedSpecialCivilians = 0;

        for (ServerPlayer player : serverWorld.players()) {
            UUID uuid = player.getUUID();
            if (selectedRoles.containsKey(uuid)) {
                SRERole role = selectedRoles.get(uuid);
                if (role != null) {
                    int roleType = PlayerRoleWeightManager.getRoleType(role);
                    if (roleType == 4) {
                        selectedKillers++;
                    } else if (roleType == 5) {
                        selectedVigilantes++;
                    } else if (roleType == 2 || roleType == 3) {
                        selectedNeutrals++;
                    } else if (isSpecialCivilianRole(role)) {
                        selectedSpecialCivilians++;
                    }
                }
            }
        }

        // 使用 SetRoleCountCommand 计算各类型目标数量
        int targetKillers = RoleCountManager.getKillerCount(totalPlayerCount);
        int targetVigilantes = RoleCountManager.getVigilanteCount(totalPlayerCount);
        int targetNeutrals = RoleCountManager.getNeutralCount(totalPlayerCount);
        targetKillers = Math.max(1, targetKillers);
        targetVigilantes = Math.max(0, targetVigilantes);
        targetNeutrals = Math.max(0, targetNeutrals);

        // 只补充不足的部分（已达到目标则不补充）
        int neededKillers = Math.max(0, targetKillers - selectedKillers);
        int neededVigilantes = Math.max(0, targetVigilantes - selectedVigilantes);
        int neededNeutrals = Math.max(0, targetNeutrals - selectedNeutrals);
        // 特殊平民全部分配（这些职业需要优先给到场上）
        int neededSpecialCivilians = Math.max(0, remainingSpecialCivilians.size());

        int totalNeeded = neededKillers + neededVigilantes + neededNeutrals + neededSpecialCivilians;
        if (totalNeeded <= 0) {
            return;
        }

        // 获取场上已分配纯平民的玩家
        List<ServerPlayer> civilianPlayers = new ArrayList<>();
        for (ServerPlayer player : serverWorld.players()) {
            UUID uuid = player.getUUID();
            if (selectedRoles.containsKey(uuid)) {
                SRERole role = selectedRoles.get(uuid);
                if (role != null && role.isInnocent() && !role.canUseKiller() && !role.isVigilanteTeam()
                        && !role.isNeutrals() && !isSpecialCivilianRole(role)) {
                    civilianPlayers.add(player);
                }
            }
        }

        if (civilianPlayers.isEmpty()) {
            return;
        }

        Random random = new Random(serverWorld.getGameTime());

        // 构建需要分配的优先职业列表（只取需要的数量）
        ArrayList<SRERole> toAssign = new ArrayList<>();
        for (int i = 0; i < neededKillers && i < remainingKillers.size(); i++) {
            toAssign.add(remainingKillers.get(i));
        }
        for (int i = 0; i < neededVigilantes && i < remainingVigilantes.size(); i++) {
            toAssign.add(remainingVigilantes.get(i));
        }
        for (int i = 0; i < neededNeutrals && i < remainingNeutrals.size(); i++) {
            toAssign.add(remainingNeutrals.get(i));
        }
        for (int i = 0; i < neededSpecialCivilians && i < remainingSpecialCivilians.size(); i++) {
            toAssign.add(remainingSpecialCivilians.get(i));
        }

        // 随机分配优先职业给纯平民
        for (SRERole priorityRole : toAssign) {
            if (civilianPlayers.isEmpty()) {
                break;
            }

            ServerPlayer targetPlayer = civilianPlayers.remove(random.nextInt(civilianPlayers.size()));
            UUID targetUuid = targetPlayer.getUUID();

            // 移除该平民的旧职业（放回池子）
            SRERole oldRole = selectedRoles.get(targetUuid);
            if (oldRole != null && !isRoleInPool(oldRole)) {
                rolePool.add(oldRole);
            }

            // 分配新职业
            selectedRoles.put(targetUuid, priorityRole);
            rolePool.remove(priorityRole);

            // 通知玩家
            targetPlayer.displayClientMessage(
                    Component.translatable("gui.sre.role_rotation.role_adjusted",
                            RoleUtils.getRoleName(priorityRole).withColor(priorityRole.getColor()))
                            .withStyle(ChatFormatting.GOLD),
                    true);
        }
        doNothing(selectedSpecialCivilians);
    }

    /**
     * 神秘方法，如果你想保留某个东西又不想让他出现unused警告就请调用它！虽然没有任何用处...
     * 
     * @param __nothing
     */
    public void doNothing(Object... __nothing) {
    }

    public int getConfirmCountdown() {
        return confirmCountdown;
    }

    public void finalizeRoleSelection(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 为没有选职业的玩家自动分配
        for (UUID playerUuid : playerRotationOrder.keySet()) {
            if (!selectedRoles.containsKey(playerUuid)) {
                ServerPlayer player = serverWorld.getServer().getPlayerList().getPlayer(playerUuid);
                if (player != null && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
                    SRERole role = rolePool.isEmpty() ? TMMRoles.CIVILIAN : rolePool.get(0);
                    assignRoleToPlayer(player, role);
                    if (!rolePool.isEmpty()) {
                        rolePool.remove(0);
                    }
                }
            }
        }

        // 移除安全时间效果
        for (ServerPlayer p : serverWorld.players()) {
            p.removeEffect(ModEffects.SKILL_BANED);
            p.removeEffect(ModEffects.SAFE_TIME);
            p.removeEffect(MobEffects.INVISIBILITY);
            ServerPlayNetworking.send(p, new CloseUiPayload());
        }

        // 开始正常游戏流程
        GameUtils.recordPlayerStats(serverWorld, gameWorldComponent, new ArrayList<>(serverWorld.players()));
        int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
        GameUtils.addItemCooldowns(serverWorld, SAFE_TIME_COOLDOWN);
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(new ArrayList<>(serverWorld.players()),
                gameWorldComponent.getRoles());

        clear();
        sync();
    }

    public HashMap<UUID, SRERole> getSelectedRoles() {
        return selectedRoles;
    }

    public ArrayList<SRERole> getRolePool() {
        return rolePool;
    }

    public HashSet<UUID> getRandomChoosers() {
        return randomChoosers;
    }

    public int getFinalPhaseThreshold() {
        return finalPhaseThreshold;
    }

    // ==================== 特殊平民职业检查 ====================

    /**
     * 特殊平民职业列表：潜水员、医生、飞行员、琪露诺（BAKA）、帕秋莉、钳工
     * 这些职业在最后阶段和职业调整阶段需要被优先选取
     */
    private static final Set<SRERole> SPECIAL_CIVILIAN_ROLES = Set.of(
            ModRoles.DIVER,
            ModRoles.DOCTOR,
            ModRoles.PILOT,
            RedHouseRoles.BAKA,
            RedHouseRoles.PACHURI,
            ModRoles.FITTER);

    /**
     * 检查是否是特殊平民职业
     * 以硬编码的特殊平民职业列表 + isInnocent() && !canBeRandomed() 的平民职业为准
     */
    private boolean isSpecialCivilianRole(SRERole role) {
        if (role == null)
            return false;
        // 1. 硬编码的特殊平民职业
        ResourceLocation id = role.identifier();
        for (SRERole special : SPECIAL_CIVILIAN_ROLES) {
            if (id.equals(special.identifier())) {
                return true;
            }
        }
        // 2. 被 setCanBeRandomedByOtherRoles(false) 标记的平民职业（canBeRandomed() == false）
        if (role.isInnocent() && !role.canBeRandomed()) {
            return true;
        }
        return false;
    }

    /**
     * 检查角色是否已在池子中（基于 identifier 比较）
     */
    private boolean isRoleInPool(SRERole role) {
        if (role == null)
            return false;
        ResourceLocation id = role.identifier();
        for (SRERole r : rolePool) {
            if (id.equals(r.identifier())) {
                return true;
            }
        }
        return false;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        // 基础状态字段
        buf.writeBoolean(isSelecting);
        buf.writeVarInt(currentRotationIndex);
        buf.writeVarInt(totalPlayerCount);
        buf.writeVarInt(confirmCountdown);
        buf.writeVarInt(selectionTimeLimit);
        buf.writeLong(currentPlayerSelectionStart);

        // 玩家轮选序号 (UUID -> index)
        buf.writeVarInt(playerRotationOrder.size());
        for (Map.Entry<UUID, Integer> entry : playerRotationOrder.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }

        // 已选职业 (UUID -> role identifier string)
        buf.writeVarInt(selectedRoles.size());
        for (Map.Entry<UUID, SRERole> entry : selectedRoles.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue().identifier().toString());
        }

        // 当前候选职业
        buf.writeVarInt(currentCandidates.size());
        for (SRERole role : currentCandidates) {
            buf.writeUtf(role.identifier().toString());
        }

        // 随机选择玩家集合
        buf.writeVarInt(randomChoosers.size());
        for (UUID uuid : randomChoosers) {
            buf.writeUUID(uuid);
        }
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        isSelecting = buf.readBoolean();
        currentRotationIndex = buf.readVarInt();
        totalPlayerCount = buf.readVarInt();
        confirmCountdown = buf.readVarInt();
        selectionTimeLimit = buf.readVarInt();
        currentPlayerSelectionStart = buf.readLong();

        playerRotationOrder.clear();
        int orderSize = buf.readVarInt();
        for (int i = 0; i < orderSize; i++) {
            playerRotationOrder.put(buf.readUUID(), buf.readVarInt());
        }

        selectedRoles.clear();
        int selectedSize = buf.readVarInt();
        for (int i = 0; i < selectedSize; i++) {
            UUID uuid = buf.readUUID();
            String rolePath = buf.readUtf();
            SRERole role = TMMRoles.ROLES.get(ResourceLocation.parse(rolePath));
            if (role != null) {
                selectedRoles.put(uuid, role);
            }
        }

        currentCandidates.clear();
        int candidatesSize = buf.readVarInt();
        for (int i = 0; i < candidatesSize; i++) {
            String rolePath = buf.readUtf();
            SRERole role = TMMRoles.ROLES.get(ResourceLocation.parse(rolePath));
            if (role != null) {
                currentCandidates.add(role);
            }
        }

        randomChoosers.clear();
        int randomSize = buf.readVarInt();
        for (int i = 0; i < randomSize; i++) {
            randomChoosers.add(buf.readUUID());
        }

        // 更新客户端缓存
        updateRoleRotationCache();
    }

    @Environment(EnvType.CLIENT)
    private void updateRoleRotationCache() {
        // 更新基础状态
        RoleRotationCache.updateBaseState(isSelecting, currentRotationIndex, totalPlayerCount, confirmCountdown);

        // 更新剩余时间（使用 selectionTimeLimit 作为剩余时间，与原 RoleRotationSyncS2CPacket 行为一致）
        RoleRotationCache.setRemainingTime(selectionTimeLimit);

        // 更新 rotationOrder
        HashMap<UUID, Integer> orderMap = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : playerRotationOrder.entrySet()) {
            orderMap.put(entry.getKey(), entry.getValue());
        }
        RoleRotationCache.updateRotationOrder(orderMap);

        // 更新 selectedRoles
        HashMap<UUID, String> selectedMap = new HashMap<>();
        for (Map.Entry<UUID, SRERole> entry : selectedRoles.entrySet()) {
            selectedMap.put(entry.getKey(), entry.getValue().identifier().toString());
        }
        RoleRotationCache.updateSelectedRoles(selectedMap);

        // 更新当前候选职业
        List<String> candidatesList = new ArrayList<>();
        for (SRERole role : currentCandidates) {
            candidatesList.add(role.identifier().toString());
        }
        RoleRotationCache.updateCurrentCandidates(candidatesList);

        // 更新当前玩家序号
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int myIndex = playerRotationOrder.getOrDefault(mc.player.getUUID(), -1);
            RoleRotationCache.setMyRotationIndex(myIndex);
            // 更新 wasMyTurn 状态（用于客户端声音和 UI 逻辑）
            RoleRotationCache.setWasMyTurn(isSelecting && RoleRotationCache.isMyTurn(mc.player.getUUID()));
        }

        // 更新随机选择玩家
        RoleRotationCache.updateRandomChoosers(new HashSet<>(randomChoosers));
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isSelecting", isSelecting);
        tag.putInt("currentIndex", currentRotationIndex);
        tag.putInt("totalPlayers", totalPlayerCount);
        tag.putInt("confirmCountdown", confirmCountdown);
        // card tracking 仅服务端使用，不同步
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        isSelecting = tag.getBoolean("isSelecting");
        currentRotationIndex = tag.getInt("currentIndex");
        totalPlayerCount = tag.getInt("totalPlayers");
        confirmCountdown = tag.getInt("confirmCountdown");
    }
}
