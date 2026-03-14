package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
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
import net.minecraft.world.entity.player.Player;
import java.util.*;
import java.util.stream.Collectors;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.RoleWeightedUtil;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modded_murder.PlayerRoleAssigner;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.harpymodloader.commands.SetRoleCountCommand;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

public class StarRailMurderGameMode extends GameMode {
    public StarRailMurderGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 执行游戏结束时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:end_game");

        // 将玩家从队伍中移除
        removePlayersFromTeam(serverWorld.getServer().createCommandSourceStack(), "harpymodloader_game");
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.modifiers.clear();
        worldModifierComponent.sync();
        super.finalizeGame(serverWorld, gameWorldComponent);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        if (!Harpymodloader.isMojangVerify) {
            return;
        }
        GameInitializeEvent.EVENT.invoker().initializeGame(serverWorld, gameWorldComponent, players);

        Harpymodloader.refreshRoles();

        HarpyModLoaderConfig.HANDLER.load();

        ((SRETrainWorldComponent) SRETrainWorldComponent.KEY.get(serverWorld))
                .setTimeOfDay(SRETrainWorldComponent.TimeOfDay.MIDNIGHT);
        gameWorldComponent.clearRoleMap();
        for (ServerPlayer player : players) {
            ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
            // 暂时不直接添加角色，而是记录到映射表中
        }

        // 将所有玩家添加到队伍中
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");

        // 执行游戏开始时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");

        Harpymodloader.setRoleMaximum(TMMRoles.VIGILANTE.getIdentifier(), 100);
        assignRole(serverWorld, gameWorldComponent, players);
    }

    private void assignRole(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
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

                value.getDefaultItems().forEach(item -> key.getInventory().placeItemBackInInventory(item));
                Harpymodloader.LOGGER.debug("Assigned role " + value.getIdentifier() + " to " + key.getName());
                if (value.canUseKiller()) {
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(key);
                    playerShopComponent.setBalance(100 + playerShopComponent.balance);
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
    private void executeFunction(CommandSourceStack source, String function) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }

    // 将玩家添加到队伍的辅助方法
    private void addPlayersToTeam(CommandSourceStack source, List<ServerPlayer> players, String teamName) {
        try {
            // 首先尝试创建队伍（如果不存在）
            source.getServer().getCommands().performPrefixedCommand(source,
                    "team add " + teamName + " " + teamName + "_team");

            // 将所有玩家添加到队伍中
            for (ServerPlayer player : players) {
                source.getServer().getCommands().performPrefixedCommand(source,
                        "team join " + teamName + " " + player.getName().getString());
            }
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to manage team: " + teamName + ", error: " + e.getMessage());
        }
    }

    // 将玩家从队伍中移除的辅助方法
    private void removePlayersFromTeam(CommandSourceStack source, String teamName) {
        try {
            // 将所有玩家从队伍中移除
            source.getServer().getCommands().performPrefixedCommand(source, "team empty " + teamName);

            // 删除队伍
            source.getServer().getCommands().performPrefixedCommand(source, "team remove " + teamName);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to remove team: " + teamName + ", error: " + e.getMessage());
        }
    }

    public void assignModifiers(int desiredModifierCount, ServerLevel serverWorld,
            SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        worldModifierComponent.getModifiers().clear();

        // 使用临时映射存储要添加的修饰符，避免在遍历过程中修改数据结构
        Map<UUID, List<Modifier>> tempModifierAssignments = new HashMap<>();
        var allModifiers = new ArrayList<>(HMLModifiers.MODIFIERS);
        int killerMods = (int) allModifiers.stream().filter(modifier -> modifier.killerOnly).count();
        Collections.shuffle(allModifiers);

        for (var mod : allModifiers) {
            int playersAssigned = 0;
            int specificDesiredRoleCount = desiredModifierCount;

            ArrayList<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
            Collections.shuffle(shuffledPlayers);

            if (mod.killerOnly) {
                specificDesiredRoleCount = (int) Math.floor(Math.floor((double) players.size() / 7) / killerMods);
                specificDesiredRoleCount = Math.max(specificDesiredRoleCount, 1);
            }
            if (Harpymodloader.FORCED_MODDED_MODIFIER.containsKey(mod)) {
                for (ServerPlayer player : shuffledPlayers) {
                    if (Harpymodloader.FORCED_MODDED_MODIFIER.get(mod).contains(player.getUUID())) {
                        // 临时存储，稍后统一添加
                        tempModifierAssignments.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(mod);
                        // ModifierAssigned.EVENT.invoker().assignModifier(player, mod);
                        playersAssigned++;
                    }
                }
            }
            for (ServerPlayer player : shuffledPlayers) {
                if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(mod.identifier.toString())) {
                    break;
                }
                if (playersAssigned >= specificDesiredRoleCount) {
                    break;
                }

                if (Harpymodloader.MODIFIER_MAX.containsKey(mod.identifier)) {
                    if (playersAssigned >= Harpymodloader.MODIFIER_MAX.get(mod.identifier)) {
                        break;
                    }
                }

                boolean valid = true;

                if (mod.canOnlyBeAppliedTo != null) {
                    if (gameWorldComponent.getRole(player) != null) {
                        valid = valid && mod.canOnlyBeAppliedTo.contains(gameWorldComponent.getRole(player));
                    }
                }
                if (mod.cannotBeAppliedTo != null) {
                    if (gameWorldComponent.getRole(player) != null) {
                        valid = valid && !mod.cannotBeAppliedTo.contains(gameWorldComponent.getRole(player));
                    }
                }
                if (!valid) {
                    continue;
                }

                if (mod.killerOnly) {
                    valid = valid && gameWorldComponent.isKillerTeam(player);
                }
                if (mod.civilianOnly) {
                    valid = valid && gameWorldComponent.isInnocent(player);
                }
                if (!valid) {
                    continue;
                }
                var pModifiers = tempModifierAssignments.getOrDefault(player, null);
                if (pModifiers != null) {
                    if (pModifiers.size() >= HarpyModLoaderConfig.HANDLER.instance().modifierMaximum) {
                        continue;
                    }
                    if (pModifiers.contains(mod)) {
                        continue;
                    }
                }

                // 临时存储，稍后统一添加
                tempModifierAssignments.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(mod);
                playersAssigned++;
            }
        }

        // 统一将临时存储的修饰符添加到组件中
        for (Map.Entry<UUID, List<Modifier>> entry : tempModifierAssignments.entrySet()) {
            UUID playerUuid = entry.getKey();
            for (Modifier mod : entry.getValue()) {
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

    /**
     * 新的模块化角色分配方法
     * 处理强制角色、计算各类型角色数量、创建角色池、分配角色以及处理关联角色
     */
    private Map<Player, SRERole> assignRolesToPlayers(ServerLevel serverWorld, List<ServerPlayer> players) {
        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players) {
            roleAssignments.put(player, null);
        }

        // 第一步：处理强制分配的角色
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);
        int killerCount = SetRoleCountCommand.getKillerCount(players.size());
        int vigilanteCount = SetRoleCountCommand.getVigilanteCount(players.size());
        int neutralsCount = SetRoleCountCommand.getNatureCount(players.size());

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

        // 第二步：创建角色池并分配角色
        // 杀手池
        RoleAssignmentPool killerPool = RoleAssignmentPool.create("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);

        List<SRERole> assignedKillers = killerPool.selectRoles(killerCount);

        // 警卫池 - 使用无限重复模式，因为警卫职业数量有限
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante", SRERole::isVigilanteTeam);
        List<SRERole> assignedVigilantes = vigilantePool.selectRoles(vigilanteCount);

        // 中立池
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> (!Harpymodloader.VANNILA_ROLES.contains(role) &&
                        ((!role.canUseKiller() &&
                                !role.isInnocent()) || role.isNeutrals())
                        &&
                        role != TMMRoles.CIVILIAN));
        List<SRERole> assignedNatures = neutralsPool.selectRoles(neutralsCount);

        // 第三步：计算平民数量（只分配基础非平民角色，不包含补充的平民角色）
        int assignedSpecialCount = assignedKillers.size() + assignedVigilantes.size() + assignedNatures.size();
        int civilianCount = players.size() - assignedSpecialCount - forcedRoles.size();

        // 平民池（只包含真正的"平民"角色，例如医生等）
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);
        civilianPool.setIgnoreRoleOccupiedCount(true);
        List<SRERole> assignedCivilians = civilianPool.selectRoles(civilianCount);

        // 第四步：合并所有分配的角色（包括处理关联角色）
        List<SRERole> allRoles = new ArrayList<>();
        allRoles.addAll(assignedKillers);
        allRoles.addAll(assignedVigilantes);
        allRoles.addAll(assignedNatures);
        allRoles.addAll(assignedCivilians);

        // 展开关联角色
        List<RoleInstant> roleInstantList = new ArrayList<>();

        // 第五步：为未分配的玩家分配角色
        List<ServerPlayer> unassignedPlayers = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (roleAssignments.get(player) == null) {
                unassignedPlayers.add(player);
            }
        }

        for (SRERole role : allRoles) {
            roleInstantList.add(new RoleInstant(UUID.randomUUID(), role));
        }
        List<RoleInstant> expandedRoles = RoleAssignmentManager.expandWithCompanionRoles(roleInstantList);

        // 创建权重分布用于分配展开后的角色
        List<Map.Entry<RoleInstant, Float>> roleWeights = new ArrayList<>();

        for (var role : expandedRoles) {
            roleWeights.add(new AbstractMap.SimpleEntry<>(role,
                    HarpyModLoaderConfig.HANDLER.instance().roleWeights.getOrDefault(role.role().identifier(), 1f)));
        }

        final var collect = roleWeights
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getKey(),
                        a -> a.getValue(),
                        (existing, replacement) -> existing, // 如果键重复，保留第一个值
                        LinkedHashMap::new));
        var hashMap = new HashMap<>(collect);
        {
            var roleSelectors = new HashMap<Integer, RoleWeightedUtil>();
            {
                var roleIdToRoleMaps = new HashMap<Integer, HashMap<RoleInstant, Float>>();
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
                    RoleInstant roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
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
                        Harpymodloader.LOGGER.warn(
                                "Couldn't force player [{}]'s role to {} because there are no roles available for him.",
                                playerUid,
                                roleType);
                    }
                }
            }
        }


        RoleWeightedUtil roleSelector = new RoleWeightedUtil(hashMap);
        // 分配展开后的角色给未分配的玩家

        Collections.shuffle(unassignedPlayers);
        while (unassignedPlayers.size() > 0 && roleSelector.size() > 0) {
            RoleInstant roleInstant = roleSelector.selectRandomKeyBasedOnWeightsAndRemoved();
            SRERole selectedRole = null;
            if (roleInstant != null) {
                selectedRole = roleInstant.role();
            }
            if (selectedRole != null) {
                int selectedRoleType = PlayerRoleWeightManager.getRoleType(selectedRole);
                Player selectedPlayer = PlayerRoleAssigner.pickByInverseWeightAndRemove(unassignedPlayers,
                        selectedRoleType);
                if (selectedPlayer != null)
                    roleAssignments.put(selectedPlayer, selectedRole);
            }
        }
        for (var up : unassignedPlayers) {
            // 职业不够分配平民
            roleAssignments.put(up, TMMRoles.CIVILIAN);
        }
        return roleAssignments;
    }

    public record RoleInstant(UUID uuid, SRERole role) {

    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        boolean civilianAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            // passive money
            if (gameWorldComponent.canAutoAddMoney(player)) {
                Integer balanceToAdd = GameConstants.PASSIVE_MONEY_TICKER.apply(serverWorld.getGameTime());
                if (balanceToAdd > 0)
                    SREPlayerShopComponent.KEY.get(player).addToBalance(balanceToAdd);
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

        // game end on win and display
        GameUtils.WinStatus modifiedWinStatus = AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                winStatus, false);
        if (!modifiedWinStatus.equals(GameUtils.WinStatus.NOT_MODIFY)) {
            winStatus = modifiedWinStatus;
        }
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

}
