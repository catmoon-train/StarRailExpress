package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.GameplayExtensionEvents;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/** 纵火犯仅杀光对手才能宣胜；时间耗尽时正常走 TIME 结算，不对耗时的局锁局。 */
public final class ArsonistWinChecker {
    private ArsonistWinChecker() {}

    /** 与鹈鹕锁局同条件：存活且时刻尚未失败的纵火犯在场时锁局。 */
    public static boolean isSettlementLocked(ServerLevel level) {
        var game = SREGameWorldComponent.KEY.get(level);
        if (game.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) return false;
        for (var player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
            if (!game.isRole(player, SERoles.ARSONIST)) continue;
            var data = ArsonistRoleData.of(player);
            if (data != null && !data.settlementUnlocked) return true;
        }
        return false;
    }

    /**
     * 锁局要否决的是普通阵营胜利（杀手/乘客）；时间耗尽 TIME 不在此列，
     * 否则时间到了也会被纵火犯锁局卡住、永远不结算。
     */
    public static boolean isOrdinaryEnding(GameUtils.WinStatus status) {
        return status == GameUtils.WinStatus.KILLERS || status == GameUtils.WinStatus.PASSENGERS;
    }

    public static void registerEvent() {
        GameplayExtensionEvents.GAME_END_RESULT.register((level, proposed, result) -> {
            if (!isSettlementLocked(level)) return result;
            var game = SREGameWorldComponent.KEY.get(level);
            for (var player : level.getPlayers(GameUtils::isPlayerAliveAndSurvival)) {
                if (!game.isRole(player, SERoles.ARSONIST)) continue;
                var data = ArsonistRoleData.of(player);
                if (data == null || data.settlementUnlocked) continue;
                if (level.getServer().getPlayerList().getPlayers().stream()
                        .noneMatch(p -> p != player && GameUtils.isPlayerAliveAndSurvival(p))) {
                    data.win();
                    return GameUtils.WinStatus.CUSTOM;
                }
                // 关键修复：无人改写结算时 result 是 NOT_MODIFY，而不是本 tick 的 PASSENGERS。
                // 只看 result 会让「杀手全灭 → 乘客胜利」完全绕过锁局；这里按
                // CommandBlockDirector.filterEnd 的算法回退到 proposed 再判断。
                GameUtils.WinStatus effective = result == null || result == GameUtils.WinStatus.NOT_MODIFY
                        ? proposed : result;
                if (isOrdinaryEnding(proposed) || isOrdinaryEnding(effective)) return GameUtils.WinStatus.NONE;
                // 与阿蒙时刻一致，不撤销已经由其他独立胜利职业直接执行的结算。
                return result;
            }
            return result;
        });
    }
}
