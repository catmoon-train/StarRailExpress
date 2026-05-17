package io.wifi.starrailexpress.cca.gamemode;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.content.vote.client.RoleRotationCache;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.CloseUiPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.SetRoleCountCommand;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.*;
import java.util.stream.Collectors;

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

    public RoleRotationWorldComponent(Level world) {
        this.world = world;
    }

    public void clear() {
        this.rolePool.clear();
        this.playerRotationOrder.clear();
        this.selectedRoles.clear();
        this.currentRotationIndex = 1;
        this.totalPlayerCount = 0;
        this.isSelecting = false;
        this.currentCandidates.clear();
        this.confirmCountdown = -1;
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
        // 人数>=15时：总人数/5，最低为6；人数<15时：总人数/3，最低为6
        if (totalPlayerCount >= 15) {
            finalPhaseThreshold = Math.max(6, totalPlayerCount / 5);
        } else {
            finalPhaseThreshold = Math.max(2, totalPlayerCount / 3);
        }

        // 计算需要的杀手/警卫/中立数量
        int killerCount = SetRoleCountCommand.getKillerCount(totalPlayerCount);
        int vigilanteCount = SetRoleCountCommand.getVigilanteCount(totalPlayerCount);
        int neutralsCount = SetRoleCountCommand.getNatureCount(totalPlayerCount);
        killerCount = Math.max(1, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        // 使用阳光自选模式的RoleAssignmentPool方法来抽取职业池
        // getAllRoles会正确处理地图限制、解锁状态和角色占用数量
        List<RoleInstance> baseRoles = SREMurderGameMode.getAllRoles(killerCount, vigilanteCount, neutralsCount, totalPlayerCount, 0);
        
        // 将基础角色添加到职业池
        for (RoleInstance inst : baseRoles) {
            if (inst.role() != null) {
                rolePool.add(inst.role());
            }
        }

        // 额外再抽取5个平民职业
        ArrayList<SRERole> availableRoles = new ArrayList<>(StupidExpress.getEnableRoles(true));
        availableRoles.removeIf(role -> 
            role == null || 
            role.isOtherModeRole() || 
            !role.canBeRandomed() || 
            role == SpecialGameModeRoles.CUSTOM_PENDING ||
            role == TMMRoles.KILLER ||
            !role.isInnocent() ||
            role.isVigilanteTeam() ||
            role.canUseKiller() ||
            role.isNeutrals()
        );

        // 创建平民职业池并抽取5个
        if (!availableRoles.isEmpty()) {
            RoleAssignmentPool civilianPool = RoleAssignmentPool.createUnlimited("RoleRotationCivilian",
                    role -> availableRoles.contains(role));
            List<SRERole> extraCivilians = civilianPool.selectRoles(Math.min(5, availableRoles.size()));
            for (SRERole civilian : extraCivilians) {
                rolePool.add(civilian);
            }
        }

        // 随机分配轮选序号
        assignRotationOrder(players);
    }

    private ArrayList<RoleInstance> expandRolesToPool(ArrayList<SRERole> availableRoles, int count, int totalPlayers, ServerLevel serverWorld, int killerCount) {
        ArrayList<RoleInstance> result = new ArrayList<>();
        Random random = new Random(serverWorld.getGameTime());

        // 获取职业权重信息（基于实际玩家数）
        int vigilanteCount = SetRoleCountCommand.getVigilanteCount(totalPlayers);
        int neutralsCount = SetRoleCountCommand.getNatureCount(totalPlayers);

        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        // 分别创建各阵营职业池
        List<SRERole> killerRoles = availableRoles.stream()
                .filter(r -> PlayerRoleWeightManager.getRoleType(r) == 4)
                .collect(Collectors.toList());
        List<SRERole> vigilanteRoles = availableRoles.stream()
                .filter(r -> PlayerRoleWeightManager.getRoleType(r) == 5)
                .collect(Collectors.toList());
        List<SRERole> neutralRoles = availableRoles.stream()
                .filter(r -> PlayerRoleWeightManager.getRoleType(r) == 2 || PlayerRoleWeightManager.getRoleType(r) == 3)
                .collect(Collectors.toList());
        List<SRERole> innocentRoles = availableRoles.stream()
                .filter(r -> PlayerRoleWeightManager.getRoleType(r) == 1)
                .collect(Collectors.toList());

        // 添加杀手职业
        for (int i = 0; i < killerCount && result.size() < count; i++) {
            if (!killerRoles.isEmpty()) {
                result.add(new RoleInstance(UUID.randomUUID(), killerRoles.get(random.nextInt(killerRoles.size()))));
            } else {
                // 如果没有杀手阵营的职业，使用TMMRoles.KILLER
                result.add(new RoleInstance(UUID.randomUUID(), TMMRoles.KILLER));
            }
        }

        // 添加警长职业
        for (int i = 0; i < vigilanteCount && result.size() < count; i++) {
            if (!vigilanteRoles.isEmpty()) {
                result.add(new RoleInstance(UUID.randomUUID(), vigilanteRoles.get(random.nextInt(vigilanteRoles.size()))));
            }
        }

        // 添加中立职业
        for (int i = 0; i < neutralsCount && result.size() < count; i++) {
            if (!neutralRoles.isEmpty()) {
                result.add(new RoleInstance(UUID.randomUUID(), neutralRoles.get(random.nextInt(neutralRoles.size()))));
            }
        }

        // 添加额外5个平民职业（不能相同，不能与池中已有职业重复）
        List<SRERole> usedRoles = result.stream().map(RoleInstance::role).collect(Collectors.toList());
        List<SRERole> extraInnocents = new ArrayList<>();
        List<SRERole> availableExtra = new ArrayList<>(innocentRoles);
        availableExtra.removeIf(role -> usedRoles.contains(role));
        Collections.shuffle(availableExtra, random);
        for (int i = 0; i < 5 && i < availableExtra.size() && result.size() < count; i++) {
            SRERole extraRole = availableExtra.get(i);
            if (!extraInnocents.contains(extraRole)) {
                extraInnocents.add(extraRole);
                result.add(new RoleInstance(UUID.randomUUID(), extraRole));
            }
        }

        // 如果还不够，用平民填充
        while (result.size() < count) {
            if (!innocentRoles.isEmpty()) {
                result.add(new RoleInstance(UUID.randomUUID(), innocentRoles.get(random.nextInt(innocentRoles.size()))));
            } else {
                result.add(new RoleInstance(UUID.randomUUID(), TMMRoles.CIVILIAN));
            }
        }

        Collections.shuffle(result, random);
        return result;
    }

    private void assignRotationOrder(List<ServerPlayer> players) {
        List<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers, new Random(world.getGameTime()));

        for (int i = 0; i < shuffledPlayers.size(); i++) {
            playerRotationOrder.put(shuffledPlayers.get(i).getUUID(), i + 1);
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

        if (isFinalPhase) {
            // 最后阶段：优先从杀手/警长/中立阵营抽取
            ArrayList<SRERole> priorityRoles = new ArrayList<>();
            for (SRERole role : poolCopy) {
                int type = PlayerRoleWeightManager.getRoleType(role);
                if (type == 4 || type == 5 || type == 2 || type == 3) { // 杀手、警长、中立阵营
                    priorityRoles.add(role);
                }
            }

            if (priorityRoles.size() >= 3) {
                Collections.shuffle(priorityRoles, random);
                for (int i = 0; i < 3; i++) {
                    currentCandidates.add(priorityRoles.get(i));
                }
            } else {
                // 优先级职业不够3个，补充普通职业
                Collections.shuffle(poolCopy, random);
                for (int i = 0; i < 3 && i < poolCopy.size(); i++) {
                    currentCandidates.add(poolCopy.get(i));
                }
            }
        } else {
            // 普通阶段：从池中随机抽取3个
            Collections.shuffle(poolCopy, random);
            for (int i = 0; i < 3 && i < poolCopy.size(); i++) {
                currentCandidates.add(poolCopy.get(i));
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
            // 最后阶段：优先从杀手/警长/中立阵营抽取
            ArrayList<SRERole> priorityPool = new ArrayList<>();
            for (SRERole role : rolePool) {
                int type = PlayerRoleWeightManager.getRoleType(role);
                if (type == 4 || type == 5 || type == 2 || type == 3) {
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

        // 应用职业
        SRERoleWorldComponent roleWorldComponent = SRERoleWorldComponent.KEY.get(player.level());
        roleWorldComponent.addRole(player.getUUID(), role, false);

        // 发送消息
        MutableComponent msg = Component.translatable("gui.sre.role_rotation.selected",
                Component.literal(String.valueOf(playerRotationOrder.get(player.getUUID()))).withStyle(ChatFormatting.GOLD),
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
     * 执行职业调整阶段：把剩余池子中的杀手/中立/警长职业分配给随机平民
     */
    public void adjustRemainingRoles(ServerLevel serverWorld) {
        // 获取剩余池子中的杀手/中立/警长职业
        ArrayList<SRERole> remainingPriorityRoles = new ArrayList<>();
        ArrayList<SRERole> remainingPool = new ArrayList<>(rolePool);
        
        for (SRERole role : remainingPool) {
            int roleType = PlayerRoleWeightManager.getRoleType(role);
            // type 4=Killer, 5=Vigilante, 2=Neutral, 3=Evil/Arson
            if (roleType == 4 || roleType == 5 || roleType == 2 || roleType == 3) {
                remainingPriorityRoles.add(role);
            }
        }
        
        if (remainingPriorityRoles.isEmpty()) {
            return;
        }
        
        // 获取场上已分配职业的平民玩家
        List<ServerPlayer> civilianPlayers = new ArrayList<>();
        for (ServerPlayer player : serverWorld.players()) {
            UUID uuid = player.getUUID();
            if (selectedRoles.containsKey(uuid)) {
                SRERole role = selectedRoles.get(uuid);
                if (role != null && role.isInnocent() && !role.canUseKiller() && !role.isVigilanteTeam() && !role.isNeutrals()) {
                    civilianPlayers.add(player);
                }
            }
        }
        
        if (civilianPlayers.isEmpty()) {
            return;
        }
        
        Random random = new Random(serverWorld.getGameTime());
        
        // 随机分配剩余的杀手/中立/警长职业给平民
        for (SRERole priorityRole : new ArrayList<>(remainingPriorityRoles)) {
            if (civilianPlayers.isEmpty()) {
                break;
            }
            
            // 随机选择一个平民
            ServerPlayer targetPlayer = civilianPlayers.remove(random.nextInt(civilianPlayers.size()));
            UUID targetUuid = targetPlayer.getUUID();
            
            // 移除该平民的旧职业（从池子中移除）
            SRERole oldRole = selectedRoles.get(targetUuid);
            if (oldRole != null && !rolePool.contains(oldRole)) {
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
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(new ArrayList<>(serverWorld.players()), gameWorldComponent.getRoles());

        clear();
        sync();
    }

    public HashMap<UUID, SRERole> getSelectedRoles() {
        return selectedRoles;
    }

    public ArrayList<SRERole> getRolePool() {
        return rolePool;
    }

    public int getFinalPhaseThreshold() {
        return finalPhaseThreshold;
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
        CompoundTag tag = new CompoundTag();
        writeToSyncNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            readFromSyncNbt(tag, buf.registryAccess());

            // 同时更新 RoleRotationCache
            updateRoleRotationCache();
        }
    }

    @Environment(EnvType.CLIENT)
    private void updateRoleRotationCache() {
        // 更新基础状态
        RoleRotationCache.updateBaseState(isSelecting, currentRotationIndex, totalPlayerCount, confirmCountdown);
        
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
        }
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
        tag.putInt("finalPhaseThreshold", finalPhaseThreshold);

        // 序列化玩家序号
        ListTag orderList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : playerRotationOrder.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("uuid", entry.getKey());
            playerTag.putInt("index", entry.getValue());
            orderList.add(playerTag);
        }
        tag.put("rotationOrder", orderList);

        // 序列化已选职业
        ListTag selectedList = new ListTag();
        for (Map.Entry<UUID, SRERole> entry : selectedRoles.entrySet()) {
            CompoundTag selTag = new CompoundTag();
            selTag.putUUID("uuid", entry.getKey());
            selTag.putString("role", entry.getValue().identifier().toString());
            selectedList.add(selTag);
        }
        tag.put("selectedRoles", selectedList);

        // 序列化当前候选职业
        ListTag candidateList = new ListTag();
        for (SRERole role : currentCandidates) {
            candidateList.add(StringTag.valueOf(role.identifier().toString()));
        }
        tag.put("candidates", candidateList);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        isSelecting = tag.getBoolean("isSelecting");
        currentRotationIndex = tag.getInt("currentIndex");
        totalPlayerCount = tag.getInt("totalPlayers");
        confirmCountdown = tag.getInt("confirmCountdown");
        finalPhaseThreshold = tag.getInt("finalPhaseThreshold");

        playerRotationOrder.clear();
        if (tag.contains("rotationOrder", CompoundTag.TAG_LIST)) {
            ListTag orderList = tag.getList("rotationOrder", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < orderList.size(); i++) {
                CompoundTag playerTag = orderList.getCompound(i);
                UUID uuid = playerTag.getUUID("uuid");
                int index = playerTag.getInt("index");
                playerRotationOrder.put(uuid, index);
            }
        }

        selectedRoles.clear();
        if (tag.contains("selectedRoles", CompoundTag.TAG_LIST)) {
            ListTag selectedList = tag.getList("selectedRoles", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < selectedList.size(); i++) {
                CompoundTag selTag = selectedList.getCompound(i);
                UUID uuid = selTag.getUUID("uuid");
                String rolePath = selTag.getString("role");
                SRERole role = TMMRoles.ROLES.get(ResourceLocation.parse(rolePath));
                if (role != null) {
                    selectedRoles.put(uuid, role);
                }
            }
        }

        currentCandidates.clear();
        if (tag.contains("candidates", CompoundTag.TAG_LIST)) {
            ListTag candidateList = tag.getList("candidates", CompoundTag.TAG_STRING);
            for (int i = 0; i < candidateList.size(); i++) {
                String rolePath = candidateList.getString(i);
                SRERole role = TMMRoles.ROLES.get(ResourceLocation.parse(rolePath));
                if (role != null) {
                    currentCandidates.add(role);
                }
            }
        }
    }
}
