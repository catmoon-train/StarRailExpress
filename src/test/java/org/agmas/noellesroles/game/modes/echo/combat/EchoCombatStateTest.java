package org.agmas.noellesroles.game.modes.echo.combat;

import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoCombatStateTest {
    private static EchoCombatState.Hit down(EchoCombatState state,long now) {
        assertEquals(EchoCombatState.Hit.DAMAGED,state.hit(now,false,false));
        assertEquals(3F,state.health());
        return state.hit(now,false,false);
    }
    @Test void threeHeartsLoseOnlyOneAndHalfPerAttack() {
        var state=new EchoCombatState();
        assertEquals(6F,state.health());
        assertEquals(EchoCombatState.Hit.DAMAGED,state.hit(1,false,false));
        assertTrue(state.life().standing()); assertEquals(3F,state.snapshot().health());
        assertEquals(EchoCombatState.Hit.DOWNED,state.hit(61,false,false));
        assertEquals(0F,state.health());
        assertTrue(state.rescue(62)); assertEquals(3F,state.health());
        state.activeAction();
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(63,false,false));
    }
    @Test void nonFiniteAndNonPositiveDamageCannotCorruptHealth() {
        var state=new EchoCombatState();
        for(float amount:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,0F,-1F})
            assertEquals(EchoCombatState.Hit.IGNORED,state.damage(0,false,false,amount));
        assertEquals(6F,state.health());
        assertEquals(EchoCombatState.Hit.DAMAGED,state.damage(1,false,false,0.5F));
        assertEquals(5.5F,state.health());
    }
    @Test void twoWolvesSameTickCannotFinishDownedTarget() {
        var state=new EchoCombatState();
        assertEquals(EchoCombatState.Hit.DOWNED,down(state,10));
        assertEquals(EchoCombatState.Hit.IGNORED,state.hit(10,false,false));
        assertEquals(1360,state.bleedUntil());
        assertEquals(EchoLifeState.DOWNED,state.life());
    }
    @Test void rescuedTargetHasOnlyOneRemainingOpportunity() {
        var state=new EchoCombatState(); down(state,0);
        assertTrue(state.rescue(100)); assertEquals(EchoLifeState.WOUNDED,state.life());
        assertEquals(EchoCombatState.Hit.IGNORED,state.hit(219,false,false));
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(220,false,false));
        assertEquals(EchoCombatState.Hit.IGNORED,state.hit(221,false,false));
    }
    @Test void activeInteractionCancelsRescueProtection() {
        var state=new EchoCombatState(); down(state,0); state.rescue(100);
        state.activeAction(); assertFalse(state.protectedAt(100));
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(100,false,false));
    }
    @Test void bleedDeadlineWinsOverRescueAtExactTick() {
        var state=new EchoCombatState(); down(state,20);
        assertTrue(state.canBeRescued(1369)); assertFalse(state.rescue(1370));
        assertTrue(state.expireBleed(1370)); assertFalse(state.expireBleed(1370));
        assertFalse(state.rescue(1371)); assertEquals(EchoLifeState.ELIMINATED,state.life());
    }
    @Test void rescueBeforeDeadlineClearsBleed() {
        var state=new EchoCombatState(); down(state,20);
        assertTrue(state.rescue(1369)); assertFalse(state.expireBleed(1370));
        assertEquals(0,state.bleedUntil());
    }
    @Test void chargedReleaseKeepsThreeSecondCooldown() {
        var state=new EchoCombatState();
        assertTrue(state.beginCutter(0));
        assertTrue(EchoCutterStrike.commit(state,16));
        assertEquals(76,state.snapshot().cutterReadyAt());
        assertFalse(EchoCutterStrike.commit(state,16));
        assertFalse(state.beginCutter(75));
        assertTrue(state.beginCutter(76));
        assertTrue(EchoCutterStrike.commit(state,92));
        assertEquals(152,state.snapshot().cutterReadyAt());
    }
    @Test void controlCancelsChargeAndResistsUntilEightSecondsAfterEnd() {
        var state=new EchoCombatState(); state.beginCutter(0);
        assertTrue(state.control(12,25)); assertFalse(state.releaseCutter(12));
        assertFalse(state.canAct(36)); assertTrue(state.canAct(37));
        assertFalse(state.control(196,25)); assertTrue(state.control(197,25));
    }
    @Test void onlySuccessfulShoveConsumesOwnCooldown() {
        var state=new EchoCombatState(); assertTrue(state.shoveReady(0));
        assertTrue(state.shoveReady(1)); state.consumeShove(1);
        assertFalse(state.shoveReady(500)); assertTrue(state.shoveReady(501));
    }
    @Test void environmentResetsWolfButForceEliminates() {
        var state=new EchoCombatState();
        assertEquals(EchoCombatState.Hit.RESET_INTERCEPTOR,state.hit(0,false,true));
        assertEquals(EchoLifeState.HEALTHY,state.life()); assertTrue(state.controlled(59));
        assertFalse(state.controlled(60)); assertEquals(EchoCombatState.Hit.IGNORED,state.hit(199,false,true));
        assertEquals(EchoCombatState.Hit.RESET_INTERCEPTOR,state.hit(200,false,true));
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(201,true,true));
    }
    @Test void forceBypassesFirstOpportunityAndProtection() {
        var state=new EchoCombatState();
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(0,true,false));
        var rescued=new EchoCombatState(); down(rescued,0); rescued.rescue(1);
        assertEquals(EchoCombatState.Hit.ELIMINATED,rescued.hit(1,true,false));
    }
    @Test void terminalDepartureCannotBeHitRescuedOrControlled() {
        for(var life:new EchoLifeState[]{EchoLifeState.ESCAPED,EchoLifeState.RETIRED,EchoLifeState.ELIMINATED}) {
            var state=new EchoCombatState(); state.depart(life);
            assertEquals(EchoCombatState.Hit.IGNORED,state.hit(1000,true,false));
            assertFalse(state.rescue(1000)); assertFalse(state.control(1000,25)); assertFalse(state.canAct(1000));
        }
    }
    @Test void frozenClockDoesNotAdvanceBleedingChargeOrControl() {
        var clock=new EchoRoundClock(); var state=new EchoCombatState();
        down(state,0);
        for(int n=0;n<1000;n++) { clock.capture(n,100,true); clock.finish(100,true); }
        assertEquals(0,clock.matchTick()); assertFalse(state.expireBleed(clock.matchTick()));
        var wolf=new EchoCombatState(); wolf.beginCutter(clock.matchTick());
        assertFalse(wolf.releaseCutter(clock.matchTick(),12));
    }
    @Test void repeatedUnderchargedCancellationsNeverConsumeCooldown() {
        var state=new EchoCombatState();
        for(int now=0;now<100;now++) {
            assertTrue(state.beginCutter(now)); assertFalse(state.releaseCutter(now+1,12));
            assertEquals(0,state.snapshot().cutterReadyAt());
        }
        assertFalse(state.releaseCutter(1000,12),"兼容蓄力接口取消后不能靠旧时间出手");
    }
    @Test void specializationAndInstinctCannotReduceThreeSecondFloor() {
        for(var spec:new org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization[]{
                org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization.HUNTER,
                org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization.SABOTEUR}) {
            var state=new EchoCombatState(spec.combat(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT));
            int charge=state.cutterChargeTicks();
            assertTrue(state.beginCutter(0)); assertTrue(state.releaseCutter(charge,charge));
            assertTrue(state.snapshot().cutterReadyAt()>=charge+60);
            assertFalse(state.beginCutter(charge+59),"任何专长都不能把 3 秒地板压掉");
        }
    }
    @Test void everyActualHitAppliesSixSecondsButProtectionAndFriendlyResetDoNot() {
        var attacker=new EchoCombatState(); var victim=new EchoCombatState();
        assertTrue(attacker.beginCutter(0)); assertTrue(attacker.releaseCutter(16));
        assertTrue(attacker.recordCutterHit(16,victim.hit(16,false,false)),"首次受伤也算命中");
        assertEquals(136,attacker.snapshot().cutterReadyAt());
        assertTrue(attacker.recordCutterHit(16,victim.hit(16,false,false)));
        assertEquals(136,attacker.snapshot().cutterReadyAt());
        assertFalse(attacker.recordCutterHit(17,victim.hit(17,false,false)));
        assertTrue(victim.rescue(20));
        assertFalse(attacker.recordCutterHit(21,victim.hit(21,false,false)),"救援保护不算命中");
        victim.activeAction();
        assertTrue(attacker.recordCutterHit(22,victim.hit(22,false,false)),"淘汰同样算命中");
        var ally=new EchoCombatState();
        assertFalse(attacker.recordCutterHit(23,ally.hit(23,false,true)),"友军复位不能触发命中冷却");
        assertEquals(142,attacker.snapshot().cutterReadyAt());
    }
    @Test void multiTargetAndDeferredHitsAlwaysTakeMaximumDeadline() {
        var attacker=new EchoCombatState();
        assertTrue(attacker.beginCutter(0)); assertTrue(attacker.releaseCutter(16));
        attacker.recordCutterHit(20,down(new EchoCombatState(),20));
        assertEquals(140,attacker.snapshot().cutterReadyAt());
        attacker.recordCutterHit(20,down(new EchoCombatState(),20));
        assertEquals(140,attacker.snapshot().cutterReadyAt(),"同 tick 横扫不叠加惩罚");
        attacker.recordCutterHit(35,down(new EchoCombatState(),35));
        assertEquals(155,attacker.snapshot().cutterReadyAt(),"延迟弹命中以命中 tick 起算");
        assertFalse(attacker.releaseCutter(36,1)); assertFalse(attacker.beginCutter(154));
        assertEquals(155,attacker.snapshot().cutterReadyAt(),"后续出击不能把6秒改为3秒");
        assertTrue(attacker.beginCutter(155));
    }
    @Test void longerConfiguredCooldownIsNeverShortenedByDown() {
        var base=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.combat();
        var state=new EchoCombatState(new org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Combat(
                12,600,base.cutterRange(),base.shoveCooldownTicks(),base.shoveControlTicks(),base.controlImmunityTicks(),
                base.bleedTicks(),base.rescueTicks(),base.rescueProtectionTicks(),base.environmentControlTicks(),
                base.environmentProtectionTicks(),base.rescueRange(),base.shoveRange()));
        assertTrue(state.beginCutter(0)); assertTrue(state.releaseCutter(16));
        state.recordCutterHit(20,EchoCombatState.Hit.DOWNED);
        assertEquals(616,state.snapshot().cutterReadyAt());
    }
    @Test void classSkillCannotBypassDownCooldown() {
        var state=new EchoCombatState();
        var clazz=new org.agmas.noellesroles.game.modes.echo.classes.EchoClassState(
                org.agmas.noellesroles.game.modes.echo.classes.EchoClass.RAVAGER);
        assertTrue(state.beginCutter(0)); assertTrue(state.releaseCutter(16));
        state.recordCutterHit(16,EchoCombatState.Hit.DOWNED);
        assertTrue(clazz.activate(17));
        assertFalse(state.beginCutter(17)); assertFalse(state.beginCutter(135));
        assertTrue(state.beginCutter(136));
    }
    @Test void frozenClockKeepsWeaponRemainingAndSnapshotDeadline() {
        var clock=new EchoRoundClock(); var state=new EchoCombatState();
        state.recordCutterHit(clock.matchTick(),EchoCombatState.Hit.DOWNED);
        long savedDeadline=state.snapshot().cutterReadyAt();
        for(int n=0;n<1000;n++) { clock.capture(n,100,true); clock.finish(100,true); }
        assertEquals(120,state.cutterRemaining(clock.matchTick()));
        assertEquals(savedDeadline,state.snapshot().cutterReadyAt());
        assertFalse(state.beginCutter(clock.matchTick()));
    }
    @Test void feedbackAndKnockbackIgnoreProtectedFriendlyAndForcedHits() {
        assertTrue(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.DAMAGED,false,false));
        assertTrue(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.DOWNED,false,false));
        assertTrue(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.ELIMINATED,false,false));
        assertFalse(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.IGNORED,false,false));
        assertFalse(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.RESET_INTERCEPTOR,false,false));
        assertFalse(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.DOWNED,true,false));
        assertFalse(EchoCutterStrike.feedbackEligible(EchoCombatState.Hit.DAMAGED,false,true));
        var push=EchoCutterStrike.impulse(10,0,0,3,4);
        assertNotNull(push); assertEquals(14,push.until());
        assertTrue(EchoCutterStrike.pushing(push,10,false));
        assertTrue(EchoCutterStrike.pushing(push,13,false));
        assertFalse(EchoCutterStrike.pushing(push,11,true));
        assertFalse(EchoCutterStrike.pushing(push,14,false));
        assertNull(EchoCutterStrike.impulse(10,1,1,1,1));
    }
}
