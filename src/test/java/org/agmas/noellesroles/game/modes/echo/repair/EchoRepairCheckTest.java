package org.agmas.noellesroles.game.modes.echo.repair;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EchoRepairCheckTest {
    /** 固定随机源：按顺序吐给定值，用于确定性地构造正常/失控判定。 */
    private static Random fixed(double accidentRoll, int zoneStart) {
        return new Random(0) {
            @Override public double nextDouble() { return accidentRoll; }
            @Override public int nextInt(int bound) { return Math.floorMod(zoneStart, bound); }
        };
    }

    @Test void hitWhenPointerSitsInsideZone() {
        var check = EchoRepairCheck.begin(fixed(0.99, 0), 0, 0, 1D);
        // 判定区从 0 开始，宽度 120；tick 0 时指针在 0
        assertEquals(EchoRepairCheck.Result.HIT, check.submit(0, 0));
        // 容差内（60 刻度）仍算同步，落在判定区里就是命中
        assertEquals(EchoRepairCheck.Result.HIT, check.submit(0, 30));
        // 容差外一律 DESYNC，不会被当成命中
        assertEquals(EchoRepairCheck.Result.DESYNC, check.submit(0, 200));
    }

    @Test void zoneWrapsAroundZero() {
        var check = EchoRepairCheck.begin(fixed(0.99, 950), 0, 0, 1D);
        // 950..1070，跨越 0 点：980 和 20 都应在判定区内
        assertTrue(check.inZone(980));
        assertTrue(check.inZone(20));
        assertFalse(check.inZone(500));
    }

    @Test void accidentSpinsFasterAndNarrowsZone() {
        var normal = EchoRepairCheck.begin(fixed(0.99, 0), 0, 0, 1D);
        var accident = EchoRepairCheck.begin(fixed(0.0, 0), 0, 0, 1D);
        assertFalse(normal.accident());
        assertTrue(accident.accident());
        assertTrue(accident.speed() > normal.speed(), "失控必须更快");
        assertTrue(accident.zoneWidth() < normal.zoneWidth(), "失控判定区必须更窄");
    }

    @Test void haulerBonusesWidenZoneAndReduceAccidents() {
        var base = EchoRepairCheck.begin(fixed(0.99, 0), 0, 0, 1D);
        var hauler = EchoRepairCheck.begin(fixed(0.99, 0), 0, 8, 0.5D);
        assertTrue(hauler.zoneWidth() > base.zoneWidth(), "搬运工判定区更宽");
        // 12% 的失控概率，减半后 6%：0.10 在基线算失控，在搬运工身上不算
        assertTrue(EchoRepairCheck.begin(fixed(0.10, 0), 0, 0, 1D).accident());
        assertFalse(EchoRepairCheck.begin(fixed(0.10, 0), 0, 0, 0.5D).accident());
    }

    @Test void desyncAndExpiryAreRejected() {
        var check = EchoRepairCheck.begin(fixed(0.99, 0), 0, 0, 1D);
        // 上报一个与权威位置相差半圈的位置
        assertEquals(EchoRepairCheck.Result.DESYNC, check.submit(0, 500));
        assertTrue(check.expired(EchoRepairCheck.TIMEOUT_TICKS + 1));
        assertEquals(EchoRepairCheck.Result.EXPIRED,
                check.submit(EchoRepairCheck.TIMEOUT_TICKS + 1, 0));
    }

    @Test void positionAdvancesWithTicksAndWraps() {
        var check = EchoRepairCheck.begin(fixed(0.99, 0), 0, 0, 1D);
        int speed = check.speed();
        assertEquals(speed, check.positionAt(1));
        assertEquals((speed * 3) % EchoRepairCheck.FULL_CIRCLE, check.positionAt(3));
        long laps = EchoRepairCheck.FULL_CIRCLE / speed + 5;
        int wrapped = check.positionAt(laps);
        assertTrue(wrapped >= 0 && wrapped < EchoRepairCheck.FULL_CIRCLE, "位置必须回绕到一圈内");
    }
}
