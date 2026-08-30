package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveKillerPolicyTest {
    @Test
    void killerPrioritizesAKnifeBeforeOptionalCoverTools() {
        assertEquals(List.of(
                FakeSteveKillerPolicy.Purchase.KNIFE,
                FakeSteveKillerPolicy.Purchase.PSYCHO,
                FakeSteveKillerPolicy.Purchase.BLACKOUT,
                FakeSteveKillerPolicy.Purchase.GUN), FakeSteveKillerPolicy.purchasePriority());
    }

    @Test
    void aCrowdTriggersTheCombinedPsychoAndBlackoutPurchase() {
        assertEquals(List.of(FakeSteveKillerPolicy.Purchase.PSYCHO,
                FakeSteveKillerPolicy.Purchase.BLACKOUT),
                FakeSteveKillerPolicy.crowdPurchasePlan(3));
        assertEquals(List.of(), FakeSteveKillerPolicy.crowdPurchasePlan(1));
    }

    @Test
    void knifeStrikeWaitsForItsChargeAndHolstersAfterTheKill() {
        assertFalse(FakeSteveKillerPolicy.canStrikeWithKnife(100L, 108L));
        assertTrue(FakeSteveKillerPolicy.canStrikeWithKnife(108L, 108L));
        assertTrue(FakeSteveKillerPolicy.shouldHolsterAfterKnifeKill(116L, 116L));
    }

    @Test
    void activeKillerHuntNeverTargetsImpostorsOrNormalKillerRoles() {
        assertTrue(FakeSteveKillerPolicy.canActivelyHunt(false, false));
        assertFalse(FakeSteveKillerPolicy.canActivelyHunt(true, false));
        assertFalse(FakeSteveKillerPolicy.canActivelyHunt(false, true));
    }

    @Test
    void aSuccessfulKillerRevolverShotDropsTheConsumedOneShotGun() {
        assertTrue(FakeSteveKillerPolicy.shouldDropKillerRevolver(true, true, true));
        assertFalse(FakeSteveKillerPolicy.shouldDropKillerRevolver(false, true, true));
        assertFalse(FakeSteveKillerPolicy.shouldDropKillerRevolver(true, false, true));
        assertFalse(FakeSteveKillerPolicy.shouldDropKillerRevolver(true, true, false));
    }

    @Test
    void skillsOnlyFireAtARealTargetInsideASafeWindow() {
        assertTrue(FakeSteveKillerPolicy.shouldUseSkill(true, true, true));
        assertFalse(FakeSteveKillerPolicy.shouldUseSkill(true, true, false));
        assertFalse(FakeSteveKillerPolicy.shouldUseSkill(true, false, true));
    }

    @Test
    void aCloseUnwitnessedArmedKillCanInterruptDisguiseWork() {
        assertTrue(FakeSteveKillerPolicy.shouldInterruptTask(true, true, true, 6.0D));
        assertFalse(FakeSteveKillerPolicy.shouldInterruptTask(true, true, true, 12.0D));
        assertFalse(FakeSteveKillerPolicy.shouldInterruptTask(true, false, true, 3.0D));
    }
}
