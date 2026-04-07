package io.wifi.starrailexpress.game.modes.funny;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public class SRECustomRoleGameMode extends SREMurderGameMode {
    public SRECustomRoleGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    long roleSelectTimeout = -1;

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        int safeTick = SREConfig.instance().customRoleModeForceSelectTime * 20;
        roleSelectTimeout = serverWorld.getGameTime() + safeTick;
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
            MCItemsUtils.insertStackInFreeSlot(player, ModItems.ONCE_REVOLVER.getDefaultInstance());
            player.addEffect(new MobEffectInstance(
                    ModEffects.NO_COLLIDE,
                    safeTick,
                    1,
                    true, // ambient - 环境效果（粒子更少更透明）
                    false, // showParticles - 不显示粒子
                    false // showIcon - 不显示图标
            ));
        }
        gameWorldComponent.syncRoles();
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.finalizeGame(serverWorld, gameWorldComponent);
        roleSelectTimeout = -1;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (roleSelectTimeout != -1) {
            if (serverWorld.getGameTime() >= roleSelectTimeout) {
                var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                        && gameWorldComponent.isRole(p, ModRoles.GAMBLER));
                if (gamblerPlayers != null && !gamblerPlayers.isEmpty()) {
                    for (var p : gamblerPlayers) {
                        GameUtils.killPlayer(p, true, null, Noellesroles.id("gamble_self_kill"));
                    }
                }
                roleSelectTimeout = -1;
            }
        }

        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    @Override
    public GameUtils.WinStatus allowGameEnd(ServerLevel serverWorld, GameUtils.WinStatus winStatus,
            boolean isLooseEndsMode, SREGameWorldComponent gameWorldComponent) {
        if (roleSelectTimeout != -1) {
            var gamblerPlayers = serverWorld.getPlayers((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                    && gameWorldComponent.isRole(p, ModRoles.GAMBLER));
            if (!gamblerPlayers.isEmpty()) {
                if (serverWorld.getGameTime() <= roleSelectTimeout) {
                    return GameUtils.WinStatus.NONE;
                }
            }
        }

        return AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld,
                winStatus, false);
    }
}
