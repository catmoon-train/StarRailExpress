package org.agmas.noellesroles.game.command_block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 仅依赖纯规则，不启动 Minecraft，也不需要注册表。 */
class PendulumRulesTest {
    @Test
    void rewindUsesActualElapsedTimeAndThreeSecondsPerHit() {
        assertEquals(480L, PendulumRules.rewindTicks(480L, 0L));
        assertEquals(420L, PendulumRules.rewindTicks(480L, 1L));
        assertEquals(120L, PendulumRules.rewindTicks(480L, 6L));
        assertEquals(0L, PendulumRules.rewindTicks(480L, 8L));
        assertEquals(0L, PendulumRules.rewindTicks(480L, 100L));
        // 提前关闭与钟锤延长都使用真实持续时间，不固定按二十四秒回溯。
        assertEquals(40L, PendulumRules.rewindTicks(100L, 1L));
        assertEquals(520L, PendulumRules.rewindTicks(580L, 1L));
        assertEquals(0L, PendulumRules.rewindTicks(0L, 0L));
        assertEquals(0L, PendulumRules.rewindTicks(480L, Long.MAX_VALUE));
    }

    @Test
    void threeStoredCastsDoNotResetTheFirstRecoveryDeadline() {
        var charges = new PendulumRules.Charges(3, 0L);
        charges = charges.spend(100L);
        assertEquals(new PendulumRules.Charges(2, 500L), charges);
        charges = charges.spend(110L).spend(120L);
        assertEquals(new PendulumRules.Charges(0, 500L), charges);
        assertEquals(charges, charges.spend(130L));
        assertEquals(charges, charges.recover(499L));
        assertEquals(new PendulumRules.Charges(1, 900L), charges.recover(500L));
    }

    @Test
    void spentChargesRecoverRepeatedlyInsteadOfBeingThreeLifetimeUses() {
        var charges = new PendulumRules.Charges(3, 0L).spend(100L).spend(100L).spend(100L);
        charges = charges.recover(500L).spend(500L);
        assertEquals(new PendulumRules.Charges(0, 900L), charges);
        charges = charges.recover(900L).spend(900L);
        assertEquals(new PendulumRules.Charges(0, 1300L), charges);
        assertEquals(new PendulumRules.Charges(3, 0L), charges.recover(10000L));
    }

    @Test
    void fullStorageStopsTheTimerAndTheNextCastStartsANewOne() {
        var charges = new PendulumRules.Charges(3, 0L).spend(100L).recover(500L);
        assertEquals(new PendulumRules.Charges(3, 0L), charges.recover(10000L));
        assertEquals(new PendulumRules.Charges(2, 10400L), charges.spend(10000L));
        assertEquals(new PendulumRules.Charges(2, 900L),
                new PendulumRules.Charges(3, 0L).spend(100L).spend(500L));
    }
}
