package io.wifi.starrailexpress.game.modes;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.GameMode;
import net.exmo.sre.repair.role.RepairRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.RoleWeightedUtil;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.harpymodloader.modded_murder.PlayerRoleAssigner;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.role.ModRoles;

import java.util.*;
import java.util.stream.Collectors;

public class SREMurderGameMode extends GameMode {
    private static final int VIGILANTE_PASSIVE_MONEY_AMOUNT = 5;
    private static final int VIGILANTE_PASSIVE_MONEY_INTERVAL_TICKS = 15 * 20;

    public SREMurderGameMode(ResourceLocation identifier) {
        super(identifier, SREConfig.instance().defaultStartTimeForMurder, 6);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return true;
    }

    public SREMurderGameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        super(identifier, defaultStartTime, minPlayerCount);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 执行游戏结束时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:end_game");
        MurderTimeEventComponent.KEY.get(serverWorld).clear();

        // 将玩家从队伍中移除
        removePlayersFromTeam(serverWorld.getServer().createCommandSourceStack(), "harpymodloader_game");
        super.finalizeGame(serverWorld, gameWorldComponent);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // if (!Harpymodloader.isMojangVerify) {
        // return;
        // }

        Harpymodloader.refreshRoles();

        gameWorldComponent.clearRoleMap();

        // 将所有玩家添加到队伍中
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");

        // 执行游戏开始时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");
        MurderTimeEventComponent.KEY.get(serverWorld).initializeDefaults();

        Harpymodloader.setRoleMaximum(ModRoles.SHERIFF_ID, 100);
        assignRole(serverWorld, gameWorldComponent, players);
    }

    public static void assignRole(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 新的模块化角色分配流程
        Map<Player, SRERole> roleAssignments = assignRolesToPlayers(serverWorld, players);
        OnGamePlayerRolesConfirm.EVENT.invoker().beforeAssignRole(serverWorld, roleAssignments);
        // 计算有特殊角色的玩家数量（用于AnnounceWelcomePayload）
        long killCount = roleAssignments.values().stream()
                .filter(role -> role != null && role != TMMRoles.CIVILIAN && role.canUseKiller())
                .count();

        // 统一应用角色分配并触发相应事件
        for (Map.Entry<Player, SRERole> entry : roleAssignments.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value != null) {
                gameWorldComponent.addRole(key, value, false);
                // 所有职业的初始物品已通过 INITIAL_ITEMS_MAP 在 assignModdedRole 事件中发放，此处跳过避免重复
                Harpymodloader.LOGGER.debug("Assigned role " + value.getIdentifier() + " to " + key.getName());
                if (value.canUseKiller()) {
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(key);
                    if (playerShopComponent.balance < GameConstants.getMoneyStart())
                        playerShopComponent.setBalance(GameConstants.getMoneyStart());
                }
            } else {
                // 如果没有分配角色，则分配默认平民角色
                gameWorldComponent.addRole(key, TMMRoles.CIVILIAN, false);
                Harpymodloader.LOGGER
                        .debug("Assigned role " + TMMRoles.CIVILIAN.getIdentifier() + " to " + key.getName());
            }
        }

        gameWorldComponent.syncRoles();
        // 同步职业

        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            var roleType = PlayerRoleWeightManager.getRoleType(role);
            PlayerRoleWeightManager.addWeight(player, roleType, 1);
            // PlayerRoleWeightManager.
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.getIdentifier().toString(), (int) killCount,
                            (int) (players.size() - killCount)));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }
        // 分配修饰符（修饰符放在职业分配后）

        int modifierRoleCount = (int) ((float) players.size()
                * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);

        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    // 执行指定函数的辅助方法
    public void executeFunction(CommandSourceStack source, String function) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }

    // 将玩家添加到队伍的辅助方法
    public void addPlayersToTeam(CommandSourceStack source, List<ServerPlayer> players, String teamName) {
        try {
            var cmd = source.getServer().getCommands();
            // 首先尝试创建队伍（如果不存在）
            cmd.performPrefixedCommand(source,
                    "team add " + teamName);

            // 将所有玩家添加到队伍中
            cmd.performPrefixedCommand(source,
                    "team join " + teamName + " @a");

            cmd.performPrefixedCommand(source,
                    "team modify " + teamName + " seeFriendlyInvisibles false");
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to manage team: " + teamName + ", error: " + e.getMessage());
        }
    }

    // 将玩家从队伍中移除的辅助方法
    public void removePlayersFromTeam(CommandSourceStack source, String teamName) {
        try {
            // 将所有玩家从队伍中移除
            source.getServer().getCommands().performPrefixedCommand(source, "team empty " + teamName);

            // 删除队伍
            source.getServer().getCommands().performPrefixedCommand(source, "team remove " + teamName);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to remove team: " + teamName + ", error: " + e.getMessage());
        }
    }

    public static void assignModifiers(int desiredModifierCount, ServerLevel serverWorld,
            SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.getModifiers().clear();

        // 使用临时映射存储要添加的修饰符，避免在遍历过程中修改数据结构
        Map<UUID, List<SREModifier>> tempModifierAssignments = new HashMap<>();
        int maxModifiersPerPlayer = Math.max(0, HarpyModLoaderConfig.HANDLER.instance().modifierMaximum);
        var allModifiers = new ArrayList<>(HMLModifiers.MODIFIERS);
        int killerMods = (int) allModifiers.stream().filter(modifier -> modifier.killerOnly).count();
        Collections.shuffle(allModifiers);

        // 修饰符轮换名单接管：仅当名单启用且管理员已在名单中配置了至少一个修饰符时，
        // 才由名单决定修饰符的启用/禁用（取代 disabledModifiers），但数量仍沿用 MODIFIER_MAX，
        // 地图限制也仍然生效。未配置任何修饰符时保持原有行为，避免老名单升级后修饰符全部消失。

        ArrayList<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
        for (var mod : allModifiers) {
            Collections.shuffle(shuffledPlayers);
            Collections.shuffle(shuffledPlayers);

            int playersAssigned = 0;
            int specificDesiredRoleCount = desiredModifierCount;

            if (mod.killerOnly) {
                specificDesiredRoleCount = (int) Math.floor(Math.floor((double) players.size() / 7) / killerMods);
                specificDesiredRoleCount = Math.max(specificDesiredRoleCount, 1);
            }
            if (Harpymodloader.FORCED_MODDED_MODIFIER.containsKey(mod)) {
                for (ServerPlayer player : shuffledPlayers) {
                    if (Harpymodloader.FORCED_MODDED_MODIFIER.get(mod).contains(player.getUUID())) {
                        if (getAssignedModifierCount(tempModifierAssignments,
                                player.getUUID()) >= maxModifiersPerPlayer) {
                            continue;
                        }
                        // 临时存储，稍后统一添加
                        if (addModifierAssignment(tempModifierAssignments, player.getUUID(), mod)) {
                            // ModifierAssigned.EVENT.invoker().assignModifier(player, mod);
                            playersAssigned++;
                        }
                    }
                }
            }
            if (SREDisableManager.isModifierDisabled(mod)) {
                continue;
            }

            // 名单只决定修饰符是否启用（上面的过滤），数量始终沿用 MODIFIER_MAX，名单不再接管数量。
            int m_max = Harpymodloader.MODIFIER_MAX.getOrDefault(mod.identifier(), 1);
            int targetAssignments = specificDesiredRoleCount;

            if (m_max == -1 || m_max == 0)
                continue;
            if (m_max != -2) {
                targetAssignments = Math.min(targetAssignments, m_max);
            }
            if (playersAssigned >= targetAssignments) {
                continue;
            }

            List<ServerPlayer> candidates = new ArrayList<>(shuffledPlayers);
            candidates.removeIf(player -> !canAssignModifierToPlayer(mod, player, gameWorldComponent,
                    tempModifierAssignments, maxModifiersPerPlayer));
            Collections.shuffle(candidates);
            candidates.sort(Comparator.comparingInt(
                    player -> getAssignedModifierCount(tempModifierAssignments, player.getUUID())));

            for (ServerPlayer player : candidates) {
                if (playersAssigned >= targetAssignments) {
                    break;
                }
                // 临时存储，稍后统一添加
                if (addModifierAssignment(tempModifierAssignments, player.getUUID(), mod)) {
                    playersAssigned++;
                }
            }
        }

        // 统一将临时存储的修饰符添加到组件中
        for (Map.Entry<UUID, List<SREModifier>> entry : tempModifierAssignments.entrySet()) {
            UUID playerUuid = entry.getKey();
            for (SREModifier mod : entry.getValue()) {
                var p = serverWorld.getPlayerByUUID(playerUuid);
                worldModifierComponent.addModifier(playerUuid, mod, false);
                ModifierAssigned.EVENT.invoker().assignModifier(p, mod);
            }
        }

        // 等所有修饰符都添加完成后，再同步整个组件
        worldModifierComponent.sync();

        for (ServerPlayer player : players) {
            var modifiers = worldModifierComponent.getDisplayableModifiers(player);
            if (!modifiers.isEmpty()) {
                MutableComponent modifiersText = Component.translatable("announcement.star.modifier")
                        .withStyle(ChatFormatting.GRAY)
                        .append(ComponentUtils.formatList(modifiers, Component.literal(", "),
                                modifier -> modifier.getName(false).withColor(modifier.color)));
                player.displayClientMessage(modifiersText, true);
            } else {
                if (!HMLModifiers.MODIFIERS.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("announcement.star.no_modifiers")
                                    .withStyle(ChatFormatting.DARK_GRAY),
                            true);
                }
            }
        }
    }

    private static boolean addModifierAssignment(Map<UUID, List<SREModifier>> modifierAssignments, UUID playerUuid,
            SREModifier modifier) {
        List<SREModifier> playerModifiers = modifierAssignments.computeIfAbsent(playerUuid, k -> new ArrayList<>());
        if (playerModifiers.contains(modifier)) {
            return false;
        }
        playerModifiers.add(modifier);
        return true;
    }

    private static boolean canAssignModifierToPlayer(SREModifier modifier, ServerPlayer player,
            SREGameWorldComponent gameWorldComponent, Map<UUID, List<SREModifier>> modifierAssignments,
            int maxModifiersPerPlayer) {
        UUID playerUuid = player.getUUID();
        if (getAssignedModifierCount(modifierAssignments, playerUuid) >= maxModifiersPerPlayer) {
            return false;
        }
        List<SREModifier> playerModifiers = modifierAssignments.get(playerUuid);
        if (playerModifiers != null) {
            if (playerModifiers.contains(modifier)) {
                return false;
            }
        }

        var role = gameWorldComponent.getRole(player);
        if (modifier.canOnlyBeAppliedTo != null && !modifier.canOnlyBeAppliedTo.isEmpty() && role != null
                && !modifier.canOnlyBeAppliedTo.contains(role)) {
            return false;
        }
        if (modifier.cannotBeAppliedTo != null && role != null && modifier.cannotBeAppliedTo.contains(role)) {
            return false;
        }
        if (modifier.killerOnly && (role == null || !role.canUseKiller())) {
            return false;
        }
        if (modifier.civilianOnly && !gameWorldComponent.isInnocent(player)) {
            return false;
        }
        if (modifier.notVigilante && gameWorldComponent.isVigilanteTeam(player)) {
            return false;
        }
        return true;
    }

    private static int getAssignedModifierCount(Map<UUID, List<SREModifier>> modifierAssignments, UUID playerUuid) {
        List<SREModifier> playerModifiers = modifierAssignments.get(playerUuid);
        return playerModifiers == null ? 0 : playerModifiers.size();
    }

    /**
     * 新的模块化角色分配方法
     * 处理强制角色、计算各类型角色数量、创建角色池、分配角色以及处理关联角色
     */
    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize) {
        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        boolean enableCivilianInPool = config.enableCivilianInPool;
        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        !role.isNeutrals() && !role.isNeutralForKiller() &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.isVigilanteTeam() && !role.isOtherModeRole() && !(role instanceof RepairRole));
        // 中立池
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> (!Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        ((!role.canUseKiller() &&
                                !role.isInnocent())
                                || role.isNeutrals())
                        &&
                        role != TMMRoles.CIVILIAN));
        // 平民池（只包含真正的"平民"角色，例如医生等）
        // 当 enableCivilianInPool 开启时，允许 sre:civilian 进入池中
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        (enableCivilianInPool || role != TMMRoles.CIVILIAN));
        // 如果开启 civilian 进池，设置最大数量为 1
        if (enableCivilianInPool) {
            Harpymodloader.setRoleMaximum(TMMRoles.CIVILIAN.getIdentifier(), 1);
        }
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, killerPool,
                neutralsPool, vigilantePool, civilianPool, true);
    }

    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, RoleAssignmentPool killerPool, RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles) {
        return getAllRoles(killerCount, vigilanteCount, neutralsCount, playerSize, forcedRoleSize, killerPool,
                neutralsPool, vigilantePool, civilianPool, haveOccupationRoles, 10);
    }

    /**
     * 新的模块化角色分配方法
     * 处理强制角色、计算各类型角色数量、创建角色池、分配角色以及处理关联角色
     */
    public static List<RoleInstance> getAllRoles(int killerCount, int vigilanteCount, int neutralsCount, int playerSize,
            int forcedRoleSize, RoleAssignmentPool killerPool, RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles,
            int maxDepth) {
        // 第二步：创建角色池并分配角色
        // 杀手池
        if (playerSize - forcedRoleSize <= 0)
            return List.of();
        List<SRERole> assignedKillers = killerPool.selectRoles(killerCount);

        // 警卫池 - 使用无限重复模式，因为警卫职业数量有限
        Harpymodloader.setRoleMaximum(ModRoles.SHERIFF_ID, 100);

        List<SRERole> assignedVigilantes = vigilantePool.selectRoles(vigilanteCount);

        List<SRERole> assignedNatures = neutralsPool.selectRoles(neutralsCount);

        // 处理 setOccupiedRoleCount(0) 的职业：这些职业不占用原有杀手/中立/警长名额，
        // 而是替换一个平民位。统计各阵营中 occupied=0 的数量，额外补充同阵营职业并减少平民数。
        // 注意：平民（非警长阵营）不应该设置 occupiedRoleCount(0)，此处做安全兜底改为1，避免平民自己"替换"平民。
        long zeroKillers = assignedKillers.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroVigilantes = assignedVigilantes.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();
        long zeroNeutrals = assignedNatures.stream().filter(r -> r.getOccupiedRoleCount() <= 0).count();

        if (zeroKillers > 0) {
            assignedKillers.addAll(killerPool.selectRoles((int) zeroKillers));
        }
        if (zeroVigilantes > 0) {
            assignedVigilantes.addAll(vigilantePool.selectRoles((int) zeroVigilantes));
        }
        if (zeroNeutrals > 0) {
            assignedNatures.addAll(neutralsPool.selectRoles((int) zeroNeutrals));
        }

        // 第三步：计算平民数量（只分配基础非平民角色，不包含补充的平民角色）
        int assignedSpecialCount = assignedKillers.size() + assignedVigilantes.size() + assignedNatures.size();
        int civilianCount = playerSize - assignedSpecialCount - forcedRoleSize;

        civilianPool.setIgnoreRoleOccupiedCount(true);
        List<SRERole> assignedCivilians = civilianPool.selectRoles(civilianCount);

        // 平民（非警长阵营）如果设置了 setOccupiedRoleCount(0)，兜底改为1，避免平民自身触发"替换平民"逻辑
        for (SRERole civilian : assignedCivilians) {
            if (!civilian.isVigilanteTeam() && civilian.getOccupiedRoleCount() <= 0) {
                civilian.setOccupiedRoleCount(1);
            }
        }

        // 第四步：合并所有分配的角色（包括处理关联角色）
        List<SRERole> allRoles = new ArrayList<>();
        allRoles.addAll(assignedKillers);
        allRoles.addAll(assignedVigilantes);
        allRoles.addAll(assignedNatures);
        allRoles.addAll(assignedCivilians);

        // 展开关联角色
        List<RoleInstance> roleInstantList = new ArrayList<>();

        for (SRERole role : allRoles) {
            roleInstantList.add(new RoleInstance(UUID.randomUUID(), role));
        }

        // 不展开，仅在父级展开
        List<RoleInstance> newRoleInstantList = RoleAssignmentManager.removeOpposingJobs(roleInstantList, killerPool,
                neutralsPool,
                vigilantePool, civilianPool, false, maxDepth);
        List<RoleInstance> resultRoleInstances = newRoleInstantList;
        if (haveOccupationRoles) {
            resultRoleInstances = RoleAssignmentManager.expandWithCompanionRoles(newRoleInstantList);
        }
        int needCivilian = (playerSize - forcedRoleSize) - resultRoleInstances.size();
        for (int i = 0; i < needCivilian; i++)
            resultRoleInstances.add(new RoleInstance(UUID.randomUUID(), TMMRoles.CIVILIAN));
        return resultRoleInstances;
    }

    private static Map<Player, SRERole> assignRolesToPlayers(ServerLevel serverWorld, List<ServerPlayer> players) {
        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players) {
            roleAssignments.put(player, null);
        }

        // 第一步：处理强制分配的角色
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);
        int killerCount = RoleCountManager.getKillerCount(players.size());
        int vigilanteCount = RoleCountManager.getVigilanteCount(players.size());
        int neutralsCount = RoleCountManager.getNeutralCount(players.size());

        // 处理强制分配的角色，减少对应角色类型的数量需求
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            Player player = serverWorld.getPlayerByUUID(entry.getKey());
            if (player != null) {
                SRERole role = entry.getValue();
                if (role != null) {
                    roleAssignments.put(player, role);

                    // 根据角色类型减少对应的数量需求
                    if (role.canUseKiller()) {
                        killerCount--;
                    } else if (role.isVigilanteTeam()) {
                        vigilanteCount--;
                    } else if (!role.isInnocent()) {
                        neutralsCount--;
                    }
                }
            }
        }

        // 确保数量不为负数
        killerCount = Math.max(0, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        List<RoleInstance> expandedRoles = getAllRoles(killerCount, vigilanteCount, neutralsCount, players.size(),
                forcedRoles.size());
        RandomSource random = serverWorld.random;
        // 第五步：为未分配的玩家分配角色
        List<ServerPlayer> unassignedPlayers = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (roleAssignments.get(player) == null) {
                unassignedPlayers.add(player);
            }
        }
        // 保底
        for (var p : players) {
            if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(p.getUUID()))
                continue;
            var manager = PlayerRoleWeightManager.playerWeights.get(p.getUUID());
            if (manager != null) {
                if (manager.getStreakCount() >= random.nextInt(4, 7)) {
                    int highestWeightType = PlayerRoleWeightManager.getHighestScoredType(p.getUUID());
                    if (highestWeightType == manager.getLastAssignedFactionGroup())
                        continue;
                    PlayerRoleWeightManager.forceTeam(p.getUUID(), highestWeightType);
                }
            }
        }
        // 创建权重分布用于分配展开后的角色
        List<Map.Entry<RoleInstance, Float>> roleWeights = new ArrayList<>();

        for (var role : expandedRoles) {
            roleWeights.add(new AbstractMap.SimpleEntry<>(role,
                    HarpyModLoaderConfig.HANDLER.instance().roleWeights.getOrDefault(role.role().identifier(), 1f)));
        }
        Collections.shuffle(roleWeights);
        final var collect = roleWeights
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getKey(),
                        a -> a.getValue(),
                        (existing, replacement) -> existing, // 如果键重复，保留第一个值
                        LinkedHashMap::new));
        var hashMap = new LinkedHashMap<>(collect);
        {
            var roleSelectors = new HashMap<Integer, RoleWeightedUtil>();
            {
                var roleIdToRoleMaps = new HashMap<Integer, HashMap<RoleInstance, Float>>();
                for (var entry : hashMap.entrySet()) {
                    var role = entry.getKey();
                    Float roleWeight = entry.getValue();
                    int roleType = PlayerRoleWeightManager.getRoleType(role.role());
                    if (!roleIdToRoleMaps.containsKey(roleType)) {
                        roleIdToRoleMaps.put(roleType, new HashMap<>());
                    }
                    roleIdToRoleMaps.get(roleType).put(role, roleWeight);
                }
                for (var entry : roleIdToRoleMaps.entrySet()) {
                    roleSelectors.putIfAbsent(entry.getKey(), new RoleWeightedUtil(entry.getValue()));
                }
            }
            {
                // 分配forceTeam
                for (var entry : PlayerRoleWeightManager.ForcePlayerTeam.entrySet()) {
                    UUID playerUid = entry.getKey();
                    var selectedPlayer = unassignedPlayers.stream().filter((p) -> p.getUUID().equals(playerUid))
                            .findFirst().orElse(null);
                    if (selectedPlayer == null)
                        continue;
                    int roleType = entry.getValue();
                    var roleSelector = roleSelectors.get(roleType);
                    if (roleSelector == null)
                        continue;
                    RoleInstance roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
                    SRERole selectedRole = null;
                    if (roleInstant != null) {
                        hashMap.remove(roleInstant);
                        selectedRole = roleInstant.role();
                        roleAssignments.put(selectedPlayer, selectedRole);
                        unassignedPlayers.remove(selectedPlayer);
                        Harpymodloader.LOGGER.debug(
                                "Assign player [{}] to {} ({})",
                                playerUid, selectedRole.getIdentifier().toString(),
                                roleType);
                    } else {
                        PlayerRoleWeightManager.boostKillerSideAfterForceFailure(playerUid);
                        Harpymodloader.LOGGER.warn(
                                "Couldn't force player [{}]'s role to {} because there are no roles available for him.",
                                playerUid,
                                roleType);
                        FactionCardType cardType = FactionCardType.fromInt(roleType);
                        if (cardType != FactionCardType.NONE) {
                            ProgressionDataManager.addFactionCard((ServerPlayer) selectedPlayer, cardType, 1);
                            BroadcastCommand.BroadcastMessage(selectedPlayer,
                                    Component.translatable("message.sre.pass.faction.assign_failed")
                                            .withStyle(ChatFormatting.RED));
                        }
                    }
                }
            }
        }
        RoleWeightedUtil roleSelector = new RoleWeightedUtil(hashMap);
        // 分配展开后的角色给未分配的玩家

        Collections.shuffle(unassignedPlayers);
        while (unassignedPlayers.size() > 0 && roleSelector.size() > 0) {
            RoleInstance roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
            SRERole selectedRole = null;
            if (roleInstant != null) {
                selectedRole = roleInstant.role();
            }
            if (selectedRole != null) {
                int selectedRoleType = PlayerRoleWeightManager.getRoleType(selectedRole);
                Player selectedPlayer = pickPlayerWithProgressBias(serverWorld, unassignedPlayers, selectedRoleType);
                if (selectedPlayer != null) {
                    unassignedPlayers.remove(selectedPlayer);
                    roleAssignments.put(selectedPlayer, selectedRole);
                    ProgressionDataManager.onRoleAssigned((ServerPlayer) selectedPlayer, selectedRole);
                }
            }
        }
        for (var up : unassignedPlayers) {
            // 职业不够分配平民
            roleAssignments.put(up, TMMRoles.CIVILIAN);
            ProgressionDataManager.onRoleAssigned((ServerPlayer) up, TMMRoles.CIVILIAN);
        }
        return roleAssignments;
    }

    public static Player pickPlayerWithProgressBias(ServerLevel serverWorld, List<ServerPlayer> unassignedPlayers,
            int selectedRoleType) {
        // 目前采用forceTeam逻辑所以无需判断
        return PlayerRoleAssigner.pickByInverseWeight(unassignedPlayers, selectedRoleType);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        boolean civilianAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            // passive money
            if (gameWorldComponent.canAutoAddMoney(player)) {
                Integer balanceToAdd = GameConstants.getPassiveMoneyTicker().apply(serverWorld.getGameTime());
                if (balanceToAdd > 0)
                    SREPlayerShopComponent.KEY.get(player).addToBalance(balanceToAdd);
            }
            if (gameWorldComponent.isRole(player, TMMRoles.VIGILANTE)
                    && !GameUtils.isPlayerEliminated(player)
                    && serverWorld.getGameTime() % VIGILANTE_PASSIVE_MONEY_INTERVAL_TICKS == 0) {
                SREPlayerShopComponent.KEY.get(player).addToBalance(VIGILANTE_PASSIVE_MONEY_AMOUNT);
            }

            // check if some civilians are still alive
            if (gameWorldComponent.isInnocent(player) && !GameUtils.isPlayerEliminated(player)) {
                civilianAlive = true;
            }
        }

        // check killer win condition (killed all civilians)
        if (!civilianAlive) {
            winStatus = GameUtils.WinStatus.KILLERS;
        }

        // check passenger win condition (all killers are dead)
        if (winStatus == GameUtils.WinStatus.NONE) {
            winStatus = GameUtils.WinStatus.PASSENGERS;
            for (UUID player : gameWorldComponent.getAllKillerPlayers()) {
                if (!GameUtils.isPlayerEliminated(serverWorld.getPlayerByUUID(player))) {
                    winStatus = GameUtils.WinStatus.NONE;
                }
            }
        }

        // 检查场上是否存在亡命徒
        if (winStatus != GameUtils.WinStatus.NONE) {
            boolean hasLooseEndAlive = false;
            ServerPlayer lastLooseEnd = null;
            int looseEndCount = 0;

            for (ServerPlayer player : serverWorld.players()) {
                if (gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)
                        && !GameUtils.isPlayerEliminated(player)) {
                    hasLooseEndAlive = true;
                    looseEndCount++;
                    lastLooseEnd = player;
                }
            }

            // 如果只有一名亡命徒存活，且没有其他存活玩家，触发亡命徒获胜
            if (hasLooseEndAlive && looseEndCount == 1 && lastLooseEnd != null) {
                // 检查是否有其他非亡命徒的存活玩家
                boolean hasOtherAlive = false;
                for (ServerPlayer player : serverWorld.players()) {
                    if (!gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)
                            && !GameUtils.isPlayerEliminated(player)) {
                        hasOtherAlive = true;
                        break;
                    }
                }
                if (!hasOtherAlive) {
                    winStatus = GameUtils.WinStatus.LOOSE_END;
                    // 补充 CustomWinnerID: loose_end
                    var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                    roundEnd.CustomWinnerID = "loose_end";
                    roundEnd.CustomWinnerPlayers.add(lastLooseEnd.getUUID());
                } else {
                    // 有其他玩家存活，游戏继续
                    winStatus = GameUtils.WinStatus.NONE;
                }
            } else if (hasLooseEndAlive) {
                // 有多个亡命徒或其他情况，游戏继续
                winStatus = GameUtils.WinStatus.NONE;
            }
        }

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        // game end on win and display
        GameUtils.WinStatus modifiedWinStatus = allowGameEnd(serverWorld, winStatus, false, gameWorldComponent);
        if (!modifiedWinStatus.equals(GameUtils.WinStatus.NOT_MODIFY)) {
            winStatus = modifiedWinStatus;
        }
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    public GameUtils.WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus,
            boolean isLooseEndsMode, SREGameWorldComponent gameWorldComponent) {
        return AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                winStatus, false);
    }
}
