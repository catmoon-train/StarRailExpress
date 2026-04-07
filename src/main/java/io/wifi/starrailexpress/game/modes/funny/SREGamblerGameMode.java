package io.wifi.starrailexpress.game.modes.funny;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public class SREGamblerGameMode extends SREMurderGameMode {
    public SREGamblerGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    long gamblerTimeout = -1;

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {

        (SRETrainWorldComponent.KEY.get(serverWorld))
                .setTimeOfDay(SRETrainWorldComponent.TimeOfDay.MIDNIGHT);
        gameWorldComponent.clearRoleMap();

        int safeTick = SREConfig.instance().gamblerModeGamblerKillTime * 20;
        gamblerTimeout = serverWorld.getGameTime() + safeTick;
        int gamblerCount = Math.clamp(
                Math.round((float) players.size() * SREConfig.instance().gamblerModeGamblerPercent), 0, players.size());
        List<ServerPlayer> unassignedPlayers = new ArrayList<>(players);

        Collections.shuffle(unassignedPlayers);

        List<ServerPlayer> gamblerPlayers = new ArrayList<>(unassignedPlayers.subList(0, gamblerCount));
        List<ServerPlayer> normalRolePlayers = new ArrayList<>();
        if (gamblerCount < unassignedPlayers.size()) {
            normalRolePlayers.addAll(unassignedPlayers.subList(gamblerCount, unassignedPlayers.size()));
        }

        for (ServerPlayer player : unassignedPlayers) {
            ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
        }

        int killerCount = normalRolePlayers.size() / 2;
        Collections.shuffle(normalRolePlayers);
        for (var player : normalRolePlayers) {
            if (killerCount > 0) {
                killerCount--;
                gameWorldComponent.addRole(player, TMMRoles.KILLER, false);
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, TMMRoles.KILLER);
            } else {
                gameWorldComponent.addRole(player, TMMRoles.VIGILANTE, false);
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, TMMRoles.VIGILANTE);
            }
        }
        for (ServerPlayer player : gamblerPlayers) {
            gameWorldComponent.addRole(player, ModRoles.GAMBLER, false);
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, ModRoles.GAMBLER);
            RoleUtils.sendWelcomeAnnouncement(player);
            MCItemsUtils.insertStackInFreeSlot(player, ModItems.ONCE_REVOLVER.getDefaultInstance());
            player.addEffect(new MobEffectInstance(
                    ModEffects.NO_COLLIDE,
                    safeTick,
                    10, // 10级别确保不会被替换
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
        }
        gameWorldComponent.syncRoles();
        int modifierRoleCount = (int) ((float) players.size()
                * HarpyModLoaderConfig.HANDLER.instance().modifierMultiplier);
        assignModifiers(modifierRoleCount, serverWorld, gameWorldComponent, players);
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
        gamblerTimeout = -1;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (gamblerTimeout != -1) {
            if (serverWorld.getGameTime() >= gamblerTimeout) {
                var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                        && gameWorldComponent.isRole(p, ModRoles.GAMBLER));
                if (gamblerPlayers != null && !gamblerPlayers.isEmpty()) {
                    for (var p : gamblerPlayers) {
                        GameUtils.killPlayer(p, true, null, Noellesroles.id("gamble_self_kill"));
                    }
                }
                gamblerTimeout = -1;
            }
        }

        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus,
            boolean isLooseEndsMode, SREGameWorldComponent gameWorldComponent) {
        if (gamblerTimeout != -1) {
            var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                    && gameWorldComponent.isRole(p, ModRoles.GAMBLER));
            if (!gamblerPlayers.isEmpty()) {
                if (serverWorld.getGameTime() <= gamblerTimeout) {
                    return GameUtils.WinStatus.NONE;
                }
            }
        }

        return AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                winStatus, false);
    }
}
