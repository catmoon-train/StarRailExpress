package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SpecialGameModeRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent.FactionCardType;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.CloseUiPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.SetRoleCountCommand;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.role.avaricious.AvariciousGoldHandler;

import java.util.*;

import static io.wifi.starrailexpress.game.GameUtils.addItemCooldowns;

public class SRECustomRoleGameMode extends SREMurderGameMode {
    public SRECustomRoleGameMode(ResourceLocation identifier) {
        super(identifier, 10, 6);
    }

    long roleSelectTimeout = -1;
    int selectionTick = -1;

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        (SRETrainWorldComponent.KEY.get(serverWorld))
                .setTimeOfDay(SRETrainWorldComponent.TimeOfDay.MIDNIGHT);
        gameWorldComponent.clearRoleMap();
        selectionTick = SREConfig.instance().customRoleModeForceSelectTime * 20;
        roleSelectTimeout = serverWorld.getGameTime() + selectionTick;
        ArrayList<ServerPlayer> unassignedPlayers = new ArrayList<>(players);
        for (ServerPlayer player : unassignedPlayers) {
            gameWorldComponent.addRole(player, SpecialGameModeRoles.CUSTOM_PENDING, false);
            CustomRoleGameModeTeamsPlayerComponent.KEY.get(player).setTeam(0);
            player.addEffect(new MobEffectInstance(
                    ModEffects.SAFE_TIME,
                    selectionTick + 20,
                    10,
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    selectionTick + 20,
                    10,
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
            player.addEffect(new MobEffectInstance(
                    ModEffects.SKILL_BANED,
                    40,
                    10,
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
            RoleUtils.sendWelcomeAnnouncement(player);
        }
        getRolesAndAssignTeams(serverWorld, gameWorldComponent, unassignedPlayers);
        gameWorldComponent.syncRoles();
        int modifierRoleCount = (int) ((float) players.size()
                * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    public void getRolesAndAssignTeams(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            ArrayList<ServerPlayer> players) {
        ArrayList<ServerPlayer> unassignedPlayers = new ArrayList<>(players);
        // 第一步：处理强制分配的角色

        int killerCount = SetRoleCountCommand.getKillerCount(players.size());
        int vigilanteCount = SetRoleCountCommand.getVigilanteCount(players.size());
        int neutralsCount = SetRoleCountCommand.getNatureCount(players.size());

        // 确保数量不为负数
        killerCount = Math.max(0, killerCount);
        vigilanteCount = Math.max(0, vigilanteCount);
        neutralsCount = Math.max(0, neutralsCount);

        List<RoleInstance> expandedRoles = super.getAllRoles(killerCount, vigilanteCount, neutralsCount, players.size(),
                0);
        HashMap<Integer, Integer> roleTypesCount = new HashMap<>();

        var crgmwc = CustomRoleGameModeWorldComponent.KEY.get(serverWorld);
        crgmwc.getRoles().clear();
        crgmwc.addAllRoles(expandedRoles);
        for (var roleInstance : expandedRoles) {
            SRERole role = roleInstance.role();
            if (role != null) {
                int roleType = PlayerRoleWeightManager.getRoleType(role);
                int nc = roleTypesCount.getOrDefault(roleType, 0);
                roleTypesCount.put(roleType, nc + 1);
            }
        }

        // 保底
        for (var p : players) {
            if (PlayerRoleWeightManager.ForcePlayerTeam.containsKey(p.getUUID()))
                continue;
            var manager = PlayerRoleWeightManager.playerWeights.get(p.getUUID());
            if (manager != null) {
                if (manager.getStreakCount() >= serverWorld.random.nextInt(4, 7)) {
                    int highestWeightType = PlayerRoleWeightManager.getHighestScoredType(p.getUUID());
                    if (highestWeightType == manager.getLastAssignedFactionGroup())
                        continue;
                    PlayerRoleWeightManager.forceTeam(p.getUUID(), highestWeightType);
                }
            }
        }

        {
            {
                // 分配forceTeam
                for (var entry : PlayerRoleWeightManager.ForcePlayerTeam.entrySet()) {
                    UUID playerUid = entry.getKey();
                    var selectedPlayer = unassignedPlayers.stream().filter((p) -> p.getUUID().equals(playerUid))
                            .findFirst().orElse(null);
                    if (selectedPlayer == null)
                        continue;
                    int roleType = entry.getValue();
                    Harpymodloader.LOGGER.debug(
                            "Assign player [{}] to {}",
                            playerUid,
                            roleType);
                    int nc = roleTypesCount.getOrDefault(roleType, 0);
                    if (nc > 0) {
                        roleTypesCount.put(roleType, nc - 1);
                        CustomRoleGameModeTeamsPlayerComponent.KEY.get(selectedPlayer).setTeamAndSync(roleType);
                        PlayerRoleWeightManager.addWeight(selectedPlayer, roleType, 1);
                        unassignedPlayers.remove(selectedPlayer);
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

        Collections.shuffle(unassignedPlayers);
        ArrayList<Integer> roleSelector = new ArrayList<>();
        for (var t : roleTypesCount.entrySet()) {
            for (int i = 0; i < t.getValue(); i++) {
                roleSelector.add(t.getKey());
            }
        }
        Collections.shuffle(roleSelector);

        while (unassignedPlayers.size() > 0 && roleSelector.size() > 0) {
            int selectedRoleType = roleSelector.getFirst();
            Player selectedPlayer = super.pickPlayerWithProgressBias(serverWorld, unassignedPlayers, selectedRoleType);
            if (selectedPlayer != null) {
                unassignedPlayers.remove(selectedPlayer);
                CustomRoleGameModeTeamsPlayerComponent.KEY.get(selectedPlayer).setTeamAndSync(selectedRoleType);
                PlayerRoleWeightManager.addWeight(selectedPlayer, selectedRoleType, 1);

                roleSelector.removeFirst();
            }
        }
        for (var up : unassignedPlayers) {
            // 职业不够分配平民
            CustomRoleGameModeTeamsPlayerComponent.KEY.get(up).setTeamAndSync(1);
            PlayerRoleWeightManager.addWeight(up, 1, 1);
        }

        crgmwc.sync();
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
        roleSelectTimeout = -1;
        CustomRoleGameModeWorldComponent.KEY.get(serverWorld).clear();
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (roleSelectTimeout != -1) {
            if (serverWorld.getGameTime() >= roleSelectTimeout) {
                trueStartAfterSelectedRoles(serverWorld, gameWorldComponent);
                roleSelectTimeout = -1;
            }
        }

        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    private void trueStartAfterSelectedRoles(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                && gameWorldComponent.isRole(p, SpecialGameModeRoles.CUSTOM_PENDING));
        if (gamblerPlayers != null && !gamblerPlayers.isEmpty()) {
            var crgmwcca = CustomRoleGameModeWorldComponent.KEY.get(serverWorld);
            for (var p : gamblerPlayers) {
                crgmwcca.autoSelect(p);
            }
        }
        SRERoleWorldComponent roleWorldComponent = SRERoleWorldComponent.KEY.get(serverWorld);
        roleWorldComponent.sync();
        List<ServerPlayer> players = serverWorld
                .getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
        for (ServerPlayer p : players) {
            SRERole role = roleWorldComponent.getRole(p);
            p.removeEffect(ModEffects.SKILL_BANED);
            p.removeEffect(ModEffects.SAFE_TIME);
            if (role != null) {
                RoleUtils.sendWelcomeAnnouncement(p);
                if (role.canUseKiller()) {
                    SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(p);
                    if (playerShopComponent.balance < GameConstants.getMoneyStart())
                        playerShopComponent.setBalance(GameConstants.getMoneyStart());
                }
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(p, role);
            }
            ServerPlayNetworking.send(p, new CloseUiPayload());
        }
        int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
        addItemCooldowns(serverWorld, SAFE_TIME_COOLDOWN);
        SRE.REPLAY_MANAGER.updateReplayInitialRoles(players, gameWorldComponent.getRoles());
        AvariciousGoldHandler.gameStartTime = -1;
        SREGameTimeComponent.KEY.get(serverWorld).addTime(selectionTick);
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus,
            boolean isLooseEndsMode, SREGameWorldComponent gameWorldComponent) {
        if (roleSelectTimeout != -1) {
            var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                    && gameWorldComponent.isRole(p, SpecialGameModeRoles.CUSTOM_PENDING));
            if (!gamblerPlayers.isEmpty()) {
                if (serverWorld.getGameTime() <= roleSelectTimeout) {
                    return GameUtils.WinStatus.NONE;
                }
            }
        }

        return AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                winStatus, false);
    }
    
    @Override
    public void recordPlayerStats(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        for (ServerPlayer player : readyPlayerList) {
            SREPlayerStatsComponent stats = SREPlayerStatsComponent.KEY.get(player);
            var crgmtpc = CustomRoleGameModeTeamsPlayerComponent.KEY.get(player);
            stats.incrementTotalGamesPlayed();
            SRERole playerRole = gameComponent.getRole(player);
            int roleType = crgmtpc.getTeam();
            if (playerRole != null) {
                stats.getOrCreateRoleStats(playerRole.identifier()).incrementTimesPlayed();

                // 统计阵营场次
                if (roleType == 5) {
                    stats.incrementTotalSheriffGames();
                } else if (roleType == 4) {
                    stats.incrementTotalKillerGames();
                } else if (roleType == 2 || roleType == 3) {
                    stats.incrementTotalNeutralGames();
                } else {
                    stats.incrementTotalCivilianGames();
                }
            }
        }
    }
}
