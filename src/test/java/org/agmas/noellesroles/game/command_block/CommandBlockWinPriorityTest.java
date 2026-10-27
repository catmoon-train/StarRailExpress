package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.wifi.starrailexpress.game.GameUtils.WinStatus;

class CommandBlockWinPriorityTest {
    @Test
    void lateNeutralWinOverridesEarlyKillerResult() {
        assertEquals(WinStatus.CUSTOM, CommandBlockWinPriority.check(() -> false,
                () -> WinStatus.KILLERS, () -> WinStatus.NOT_MODIFY, () -> WinStatus.CUSTOM));
    }

    @Test
    void lockDoesNotSkipLateNeutralWin() {
        assertEquals(WinStatus.CUSTOM, CommandBlockWinPriority.check(() -> false,
                () -> WinStatus.NONE, () -> WinStatus.CUSTOM));
    }

    @Test
    void killerResultSurvivesWhenNoNeutralHasWon() {
        assertEquals(WinStatus.KILLERS, CommandBlockWinPriority.check(() -> false,
                () -> WinStatus.KILLERS, () -> WinStatus.NOT_MODIFY, () -> null));
        assertEquals(WinStatus.NOT_MODIFY, CommandBlockWinPriority.check(() -> false,
                () -> WinStatus.NOT_MODIFY, () -> WinStatus.NOT_MODIFY));
    }

    @Test
    void existingLockStillBlocksOrdinaryTeamWin() {
        assertEquals(WinStatus.NONE, CommandBlockWinPriority.check(() -> false,
                () -> WinStatus.KILLERS, () -> WinStatus.NONE));
        assertEquals(WinStatus.NONE, CommandBlockWinPriority.check(() -> false,
                () -> WinStatus.NONE, () -> WinStatus.KILLERS));
    }

    @Test
    void independentWinStopsLaterChecksAndEachCheckRunsOnce() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(WinStatus.CUSTOM, CommandBlockWinPriority.check(() -> false,
                () -> { calls.incrementAndGet(); return WinStatus.NONE; },
                () -> { calls.incrementAndGet(); return WinStatus.CUSTOM; },
                () -> { fail("独立胜利后不能再执行可能触发结算副作用的检查"); return WinStatus.KILLERS; }));
        assertEquals(2, calls.get());
    }

    @Test
    void infectedStyleCustomKillerWinIsNotDowngraded() {
        assertEquals(WinStatus.KILLERS, CommandBlockWinPriority.check(() -> true,
                () -> WinStatus.KILLERS,
                () -> { fail("登记自定义获胜者的杀手状态也是独立结局"); return WinStatus.NONE; }));
    }

    @Test
    void onlyIndependentOutcomesHaveImmediatePriority() {
        for (WinStatus status : WinStatus.values()) {
            boolean expected = switch (status) {
                case NOT_MODIFY, NONE, TIME, NO_PLAYER, KILLERS, PASSENGERS -> false;
                default -> true;
            };
            assertEquals(expected, CommandBlockWinPriority.isIndependentWin(status, () -> false), status.name());
        }
        assertFalse(CommandBlockWinPriority.isIndependentWin(null, () -> false));
    }
}
