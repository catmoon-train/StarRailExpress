package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RepairRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent.FactionCardType;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.RoleWeightedUtil;
import org.agmas.harpymodloader.commands.SetRoleCountCommand;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.OnGamePlayerRolesConfirm;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class SREHideAndSeekGameMode extends SREMurderGameMode {
    public SREHideAndSeekGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        Harpymodloader.setRoleMaximum(ModRoles.MAGICIAN_ID, 0);
        assignRole(serverWorld, gameWorldComponent,
                players);

        int time = SREConfig.instance().hideAndSeekBaseTime;
        time += players.size() * SREConfig.instance().hideAndSeekTimePerPlayer;
        SREGameTimeComponent.KEY.get(serverWorld).setResetTime(time * 20);
        SREGameTimeComponent.KEY.get(serverWorld).reset();
        AttributeModifier hiderModifier = new AttributeModifier(
                SRE.wifiId("hide_and_seek"), SREConfig.instance().hideAndSeekHiderScale,
                AttributeModifier.Operation.ADD_VALUE);
        for (ServerPlayer p : players) {
            if (!gameWorldComponent.isKillerTeam(p)) {
                Objects.requireNonNull(p.getAttribute(Attributes.SCALE)).addPermanentModifier(hiderModifier);
            }
        }
    }

    public static void assignRole(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
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
            if (roleType == 4) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.MOVE_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));
                player.addEffect(new MobEffectInstance(
                        ModEffects.USED_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));
                player.addEffect(new MobEffectInstance(
                        ModEffects.SKILL_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));

                player.addEffect(new MobEffectInstance(
                        ModEffects.TURN_BANED,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));

                player.addEffect(new MobEffectInstance(
                        ModEffects.BLACK_MONITOR,
                        (int) (30 * 20), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        true // showIcon（显示图标）
                ));
            }
        }
        // 分配修饰符（修饰符放在职业分配后）
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    private static Map<Player, SRERole> assignRolesToPlayers(ServerLevel serverWorld, List<ServerPlayer> players) {
        Map<Player, SRERole> roleAssignments = new HashMap<>();
        for (Player player : players) {
            roleAssignments.put(player, null);
        }

        // 第一步：处理强制分配的角色
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);
        int killerCount = SetRoleCountCommand.getKillerCount(players.size());
        int vigilanteCount = SetRoleCountCommand.getVigilanteCount(players.size());
        int neutralsCount = 0;

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

        RoleAssignmentPool killerPool = RoleAssignmentPool.createUnlimited("Killer",
                role -> role.identifier() == SpecialGameModeRoles.SEEKER.identifier());
        RoleAssignmentPool vigilantePool = RoleAssignmentPool.create("Vigilante",
                role -> role.isVigilanteTeam() && !role.isOtherModeRole() && !(role instanceof RepairRole));
        // 中立池
        RoleAssignmentPool neutralsPool = RoleAssignmentPool.create("Neutrals",
                role -> (!Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        ((!role.canUseKiller() &&
                                !role.isInnocent()) || role.isNeutrals())
                        &&
                        role != TMMRoles.CIVILIAN));
        // 平民池（只包含真正的"平民"角色，例如医生等）
        RoleAssignmentPool civilianPool = RoleAssignmentPool.create("Civilian",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        !role.isOtherModeRole() &&
                        !(role instanceof RepairRole) &&
                        !role.isVigilanteTeam() &&
                        !role.canUseKiller() &&
                        !role.isNeutrals() &&
                        role.isInnocent() &&
                        role != TMMRoles.CIVILIAN);

        List<RoleInstance> expandedRoles = getAllRoles(killerCount, vigilanteCount, neutralsCount, players.size(),
                forcedRoles.size(), killerPool,
                neutralsPool, vigilantePool, civilianPool, false);

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
                            SREPlayerProgressionComponent.KEY.get(selectedPlayer).addFactionCard(cardType, 1);
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
                    SREPlayerProgressionComponent.KEY.get(selectedPlayer).onRoleAssigned(selectedRole);
                }
            }
        }
        for (var up : unassignedPlayers) {
            // 职业不够分配平民
            roleAssignments.put(up, TMMRoles.CIVILIAN);
            SREPlayerProgressionComponent.KEY.get(up).onRoleAssigned(TMMRoles.CIVILIAN);
        }
        return roleAssignments;
    }

    /**
     * 阻止躲藏者之间/谋杀者之间互相伤害，以及谋杀者死亡后返回出生点。
     * 
     * @param victim      受害者
     * @param spawnBody   生成尸体
     * @param _killer     杀手（为空认为无杀手）
     * @param deathReason 死亡原因
     * @param forceDeath  强制死亡
     */
    public void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        Level level = victim.level();
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        if (_killer != null) {
            if (gameWorldComponent.getRoleType(victim) == gameWorldComponent.getRoleType(_killer)) {
                return;
            }
            if (gameWorldComponent.isKillerTeam(victim) && gameWorldComponent.isKillerTeam(_killer))
                return;
            if (!gameWorldComponent.isKillerTeam(victim) && !gameWorldComponent.isKillerTeam(_killer))
                return;
        }
        if (gameWorldComponent.isKillerTeam(victim)) {
            GameUtils.teleportBackToRoom(victim);
            UUID killerUuid = null;
            if (_killer != null) {
                killerUuid = _killer.getUUID();
            }
            SRE.REPLAY_MANAGER.recordPlayerKill(killerUuid, victim.getUUID(), deathReason);
            SRE.REPLAY_MANAGER.recordPlayerNotKilled(
                    _killer,
                    victim,
                    deathReason);
            SREGameTimeComponent.KEY.get(level).addTime(-SREConfig.instance().hideAndSeekRewardKillRemoveTime * 20);
            return;
        }
        super.killPlayer(victim, spawnBody, _killer, deathReason, forceDeath);
    }

    @Override
    public void addKillRewardTime(SREGameTimeComponent gameTimeComponent) {
        if (gameTimeComponent.getTime() < gameTimeComponent.getResetTime()) {
            gameTimeComponent.addTime(SREConfig.instance().hideAndSeekRewardKillAddTime * 20);
        }
    }

    final public static int WARNING_DISTANCE = 5;

    @Override
    public boolean canAllPeopleSeeTime() {
        return true;
    }

    @Override
    public void tickClientGameLoop(Level world) {
        super.tickClientGameLoop(world);
        if (world.getGameTime() % 5 == 0) {
            if (SREClient.cached_player != null) {
                boolean isKillerTeam = SREClient.gameComponent.isKillerTeam(SREClient.cached_player);
                boolean flag = world.players().stream().anyMatch(
                        (p) -> p.distanceToSqr(SREClient.cached_player) <= WARNING_DISTANCE * WARNING_DISTANCE
                                && SREClient.gameComponent.isKillerTeam(p) != isKillerTeam);
                if (flag) {
                    SREClient.cached_player.displayClientMessage(Component
                            .translatable("gui.sre.gamemode.hide_and_seek", WARNING_DISTANCE,
                                    isKillerTeam ? Component.translatable("gui.sre.gamemode.hider")
                                            : Component.translatable("gui.sre.gamemode.seeker"))
                            .withStyle(ChatFormatting.GOLD), true);
                }
            }
        }
    }
}
