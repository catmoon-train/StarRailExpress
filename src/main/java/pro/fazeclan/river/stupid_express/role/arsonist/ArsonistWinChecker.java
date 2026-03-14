package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.server.level.ServerPlayer;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class ArsonistWinChecker {
    public static void registerEvent() {
        AllowGameEnd.EVENT.register((serverWorld, winStatus, isLooseEndsMode) -> {
            if (isLooseEndsMode)
                return WinStatus.NOT_MODIFY;
            var config = StupidExpress.CONFIG;
            var gameWorldComponent = SREGameWorldComponent.KEY.get(serverWorld);
            if (config.rolesSection.arsonistSection.arsonistKeepsGameGoing) {
                var players = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
                boolean arsonistAlive = false;
                for (ServerPlayer player : players) {
                    if (gameWorldComponent.isRole(player, SERoles.ARSONIST)) {
                        arsonistAlive = true;
                    }
                }

                if (players.size() == 1 && arsonistAlive) {
                    // 纵火犯独立胜利统计：使用 RoleUtils.customWinnerWin
                    StupidRoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.CUSTOM,
                            SERoles.ARSONIST.identifier().getPath(),
                            java.util.OptionalInt.of(SERoles.ARSONIST.color()));
                    return GameUtils.WinStatus.CUSTOM;
                }

                if (arsonistAlive && (winStatus == GameUtils.WinStatus.KILLERS
                        || winStatus == GameUtils.WinStatus.PASSENGERS)) {
                    return GameUtils.WinStatus.NONE;
                }
            }
            return GameUtils.WinStatus.NOT_MODIFY;
        });
    }
}
