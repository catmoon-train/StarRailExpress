package org.agmas.noellesroles.game.command_block;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.server.level.ServerLevel;

/** 命令方块模式先检查独立胜利，再采纳普通阵营结算；不新增按中立人数锁局的规则。 */
public final class CommandBlockWinPriority {
    private CommandBlockWinPriority() {
    }

    /** 包括用普通阵营状态承载、但已登记自定义获胜者的独立结局（如疫使）。 */
    public static boolean isIndependentWin(ServerLevel level, WinStatus result) {
        return isIndependentWin(result, () -> {
            String winner = SREGameRoundEndComponent.KEY.get(level).CustomWinnerID;
            return winner != null && !winner.isEmpty();
        });
    }

    static boolean isIndependentWin(WinStatus result, BooleanSupplier hasCustomWinner) {
        if (result == null) {
            return false;
        }
        return switch (result) {
            case NOT_MODIFY, NONE, TIME, NO_PLAYER -> false;
            case KILLERS, PASSENGERS -> hasCustomWinner.getAsBoolean();
            default -> true;
        };
    }

    /** 暂存普通结果：已有锁局仍有效，但不能让锁局跳过后面的独立胜利检查。 */
    public static WinStatus defer(WinStatus previous, WinStatus next) {
        if (next == null || next == WinStatus.NOT_MODIFY) {
            return previous;
        }
        if (previous == WinStatus.NOT_MODIFY || next == WinStatus.NONE) {
            return next;
        }
        return previous;
    }

    /** 顺序执行各阶段一次；已达成独立胜利时立即停止，避免重复结算副作用。 */
    @SafeVarargs
    public static WinStatus check(ServerLevel level, Supplier<WinStatus>... checks) {
        return check(() -> {
            String winner = SREGameRoundEndComponent.KEY.get(level).CustomWinnerID;
            return winner != null && !winner.isEmpty();
        }, checks);
    }

    @SafeVarargs
    static WinStatus check(BooleanSupplier hasCustomWinner, Supplier<WinStatus>... checks) {
        WinStatus deferred = WinStatus.NOT_MODIFY;
        for (Supplier<WinStatus> check : checks) {
            WinStatus result = check.get();
            if (isIndependentWin(result, hasCustomWinner)) {
                return result;
            }
            deferred = defer(deferred, result);
        }
        return deferred;
    }
}
