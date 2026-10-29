package org.agmas.noellesroles.game.halloween.pumpkin;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** 真正执行生产纯逻辑，不加载 Minecraft 注册表或 Bootstrap。 */
class PumpkinSpiritStateTest {
    @Test void targetIsFixedAndResourcesAreCumulative() {
        var state = new PumpkinSpiritState(6);
        assertEquals(24, state.target);
        assertEquals(0, state.credit(9));
        assertEquals(3, state.credit(22));
        assertEquals(31, state.collected());
        assertTrue(state.won(true));
        assertFalse(state.won(false));
        assertEquals(0, state.credit(0));
        assertEquals(0, state.credit(-2));
        assertEquals(31, state.collected());
    }

    @Test void partialPickupAndGlobalSettlementPreventDropAndShareFarming() {
        var state = new PumpkinSpiritState(4);
        int received = PumpkinAcquisition.creditable(false, 12, 5);
        state.credit(received);
        assertEquals(7, state.collected());
        // 已领取分栈即使丢掉、转给另一南瓜精也不再入账。
        assertEquals(0, PumpkinAcquisition.creditable(true, 7, 0));
        assertEquals(0, PumpkinAcquisition.creditable(true, 3, 0));
        // 未领取的地上余数仍可合法获得，满背包触碰为零。
        assertEquals(0, PumpkinAcquisition.creditable(false, 5, 5));
        state.credit(PumpkinAcquisition.creditable(false, 5, 0));
        assertEquals(12, state.collected());
    }

    @Test void reconnectReusesLedgerAndDoesNotRepeatBags() {
        UUID player = UUID.randomUUID();
        Map<UUID, PumpkinSpiritState> round = new java.util.HashMap<>();
        var before = round.computeIfAbsent(player, id -> new PumpkinSpiritState(5));
        assertEquals(2, before.credit(20));
        var after = round.computeIfAbsent(player, id -> new PumpkinSpiritState(99));
        assertSame(before, after);
        assertEquals(20, after.target);
        assertEquals(0, after.credit(1));
        assertEquals(21, after.collected());
    }

    @Test void candyStackSplitsAndSelfEatingConsumeExactlyOneUnit() {
        var ledger = new CandyLedger();
        UUID owner = UUID.randomUUID(), eater = UUID.randomUUID();
        UUID batch = ledger.issue(owner, 3);
        assertFalse(ledger.consume(batch, owner, owner));
        assertTrue(ledger.consume(batch, owner, eater));
        assertTrue(ledger.consume(batch, owner, eater));
        assertFalse(ledger.consume(batch, owner, eater));
        assertFalse(ledger.consume(UUID.randomUUID(), owner, eater));
    }

    @Test void candyRejectsForgedOwnerAndRoleRemoval() {
        var ledger = new CandyLedger();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        UUID batch = ledger.issue(owner, 2);
        assertFalse(ledger.consume(batch, other, owner));
        assertTrue(ledger.consume(batch, owner, other));
        ledger.forget(owner);
        assertFalse(ledger.consume(batch, owner, other));
    }

    @Test void headLeaseKeepsOriginalComponentsAndReleasesOnlyOnce() {
        record Equipment(int durability, Map<String, String> components) {}
        Equipment original = new Equipment(17, Map.of("enchantment", "binding", "name", "original"));
        HeadLease<Equipment> lease = new HeadLease<>(original, 300);
        lease.extend(400);
        lease.extend(200);
        assertFalse(lease.expired(300));
        assertFalse(lease.expired(399));
        assertTrue(lease.expired(400));
        assertSame(original, lease.release());
        assertNull(lease.release());
        assertFalse(lease.active());
        assertFalse(lease.expired(999));
    }

    @Test void deathDisconnectOrMapChangeCanReleaseBeforeTimeout() {
        Object original = new Object();
        HeadLease<Object> lease = new HeadLease<>(original, 400);
        assertFalse(lease.expired(0));
        assertSame(original, lease.release());
        lease.extend(900);
        assertNull(lease.release());
    }

    @Test void doorFailureWaitsForNextIndependentAttempt() {
        var state = new PumpkinSpiritState(4);
        state.died();
        assertFalse(state.door(true, false));
        assertFalse(state.door(false, true));
        assertTrue(state.door(true, true));
        state.revived();
        assertFalse(state.door(true, true));
    }

    @Test void doorCoinHasBothOutcomesAndDoesNotBecomeGuaranteedAfterFailure() {
        Random random = new Random(4382);
        int wins = 0;
        var state = new PumpkinSpiritState(4);
        state.died();
        for (int i = 0; i < 10000; i++) if (state.door(true, random.nextBoolean())) wins++;
        assertTrue(wins > 4800 && wins < 5200);
    }

    @Test void desperadoKillsOnceAndRevivesAfterItDisappears() {
        var state = new PumpkinSpiritState(2);
        state.credit(8);
        assertTrue(state.suppress(true, true));
        assertFalse(state.won(true));
        state.died();
        assertFalse(state.suppress(true, false));
        assertFalse(state.door(true, true));
        assertFalse(state.needsSuppressionRevive());
        state.suppress(false, false);
        assertTrue(state.needsSuppressionRevive());
        // 安全位置暂时找不到：请求保留，不能改用敲门绕过。
        assertFalse(state.door(true, true));
        state.suppress(false, false);
        assertTrue(state.needsSuppressionRevive());
        state.revived();
        assertFalse(state.needsSuppressionRevive());
        assertTrue(state.won(true));
    }

    @Test void alreadyDeadPumpkinIsAlsoSuppressedAndAutomaticallyReturns() {
        var state = new PumpkinSpiritState(3);
        state.died();
        assertFalse(state.suppress(true, false));
        assertFalse(state.door(true, true));
        state.suppress(false, false);
        assertTrue(state.needsSuppressionRevive());
    }

    @Test void attackCooldownIsPerTargetAndUsesPausedLogicalClock() {
        var state = new PumpkinSpiritState(4);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(state.attack(a, 100));
        assertFalse(state.attack(a, 100));
        assertTrue(state.attack(b, 100));
        assertFalse(state.attack(a, 399));
        assertTrue(state.attack(a, 400));
    }
}
