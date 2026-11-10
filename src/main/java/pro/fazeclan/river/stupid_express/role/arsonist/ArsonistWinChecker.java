package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.GameplayExtensionEvents;
import io.wifi.starrailexpress.game.GameUtils;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/** 纵火犯仅杀光对手才能宣胜；时刻失败后放行普通阵营及超时结算。 */
public final class ArsonistWinChecker {
    private ArsonistWinChecker() {}

    public static void registerEvent() {
        GameplayExtensionEvents.GAME_END_RESULT.register((level, proposed, result) -> {
            var game = SREGameWorldComponent.KEY.get(level);
            if (game.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) return result;
            for (var player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                if (!game.isRole(player, SERoles.ARSONIST)) continue;
                var data = ArsonistRoleData.of(player);
                if (data == null || data.settlementUnlocked) continue;
                if (level.getServer().getPlayerList().getPlayers().stream()
                        .noneMatch(p -> p != player && GameUtils.isPlayerAliveAndSurvival(p))) {
                    data.win();
                    return GameUtils.WinStatus.CUSTOM;
                }
                // 与阿蒙时刻一致，不撤销已经由其他独立胜利职业直接执行的结算。
                if (result == GameUtils.WinStatus.KILLERS || result == GameUtils.WinStatus.PASSENGERS
                        || result == GameUtils.WinStatus.TIME) return GameUtils.WinStatus.NONE;
                return result;
            }
            return result;
        });
    }
}
