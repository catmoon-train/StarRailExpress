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
    void killerNeutralsAndAllFakeAlliesAreNeitherPreyNorWitnesses() {
        assertFalse(FakeSteveKillerPolicy.canActivelyHunt(false, false, true));
        assertFalse(FakeSteveKillerPolicy.countsAsHostileWitness(true, false, false));
        assertFalse(FakeSteveKillerPolicy.countsAsHostileWitness(false, true, false));
        assertFalse(FakeSteveKillerPolicy.countsAsHostileWitness(false, false, true));
        assertTrue(FakeSteveKillerPolicy.countsAsHostileWitness(false, false, false));
    }

    @Test
    void aSuccessfulKillerRevolverShotDropsTheConsumedOneShotGun() {
        assertTrue(FakeSteveKillerPolicy.shouldDropKillerRevolver(true, true, true));
        assertFalse(FakeSteveKillerPolicy.shouldDropKillerRevolver(false, true, true));
        assertFalse(FakeSteveKillerPolicy.shouldDropKillerRevolver(true, false, true));
        assertFalse(FakeSteveKillerPolicy.shouldDropKillerRevolver(true, true, false));
    }

    @Test
    void psychoChainsKillsWithoutTheOrdinaryWeaponRecoveryPause() {
        assertEquals(0, FakeSteveKillerPolicy.recoveryTicksAfterKill(true));
        assertEquals(40, FakeSteveKillerPolicy.recoveryTicksAfterKill(false));
        assertEquals(2, FakeSteveKillerPolicy.psychoAttackCooldownTicks());
    }

    @Test
    void psychoIgnoresWitnessRiskAndInterruptsDisguiseForAnotherTarget() {
        assertTrue(FakeSteveKillerPolicy.canHuntThroughWitnesses(true, true));
        assertFalse(FakeSteveKillerPolicy.canHuntThroughWitnesses(false, true));
        assertTrue(FakeSteveKillerPolicy.shouldPsychoInterruptTask(true, true));
        assertFalse(FakeSteveKillerPolicy.shouldPsychoInterruptTask(false, true));
    }

    @Test
    void aCarriedDerringerEntersTheSameImmediateRiskFreeBerserkMode() {
        assertTrue(FakeSteveKillerPolicy.entersDerringerBerserk(true));
        assertFalse(FakeSteveKillerPolicy.entersDerringerBerserk(false));
        assertTrue(FakeSteveKillerPolicy.isBerserk(false, true));
        assertTrue(FakeSteveKillerPolicy.ignoresRisk(false, true));
        assertEquals(1, FakeSteveKillerPolicy.decisionCadenceTicks(
                FakeSteveKillerPolicy.isBerserk(false, true)));
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
