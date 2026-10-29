package org.agmas.noellesroles.game.modes.echo.cards;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoEffectState.*;

class EchoEffectStateTest {
    @Test void cancelledActionDoesNotConsumeMarker() {
        var s=new EchoEffectState(); var mark=new Mark("silent_rig",1200,2,0,false,0);
        assertTrue(s.add(Marker.SILENCE,mark,0));
        s.expire(100); assertEquals(2,s.mark(Marker.SILENCE,100).orElseThrow().charges());
        assertTrue(s.consume(Marker.SILENCE,101)); assertEquals(1,s.mark(Marker.SILENCE,102).orElseThrow().charges());
        assertTrue(s.consume(Marker.SILENCE,103)); assertTrue(s.mark(Marker.SILENCE,103).isEmpty());
        assertFalse(s.consume(Marker.SILENCE,104));
    }
    @Test void exactDeadlineExpiresWithoutRefundOrRefresh() {
        var s=new EchoEffectState(); assertTrue(s.add(Marker.TRADE,new Mark("pact",1200,1,40,false,16),0));
        for(int i=0;i<100;i++) s.expire(100); // 暂停期间重复同一tick，不按墙钟到期。
        assertEquals(16,s.mark(Marker.TRADE,1199).orElseThrow().range());
        assertTrue(s.mark(Marker.TRADE,1200).isEmpty()); assertFalse(s.consume(Marker.TRADE,1200));
    }
    @Test void pendingMarkerRejectsAnySecondMarkerIncludingUpgrade() {
        var s=new EchoEffectState(); assertTrue(s.add(Marker.SILENCE,new Mark("base",1200,1,0,false,0),0));
        assertFalse(s.add(Marker.SILENCE,new Mark("up",1300,2,0,false,0),100));
        assertEquals("base",s.mark(Marker.SILENCE,100).orElseThrow().source());
    }
    @Test void mobilityUpgradeReplacesInsteadOfStackingAndRejectsDowngrade() {
        var s=new EchoEffectState(); assertTrue(s.boost(new Boost("short",1,60,0.25),0));
        assertFalse(s.boost(new Boost("short",1,70,0.25),10));
        assertTrue(s.boost(new Boost("long",2,110,0.35),10));
        assertFalse(s.boost(new Boost("short",1,80,0.25),20));
        assertEquals(0.25,s.speedDelta(20,0.10),0.00001);
        assertEquals(-0.10,s.speedDelta(110,0.10),0.00001);
    }
    @Test void slowIsSingleSourceAndSpeedClampsFinalAdditiveBaseline() {
        var s=new EchoEffectState(); assertTrue(s.boost(new Boost("dash",2,100,0.35),0));
        assertTrue(s.slow(new Boost("hunt",2,160,0.10),0)); assertFalse(s.slow(new Boost("hunt",2,200,0.10),1));
        assertEquals(0.15,s.speedDelta(1,0.10),0.00001); assertEquals(-0.20,s.speedDelta(100,0.10),0.00001);
        assertEquals(-0.10,s.speedDelta(160,0.10),0.00001);
        assertEquals(-0.5,s.speedDelta(160,5),0.00001);
        assertEquals(0.5,s.speedDelta(160,-5),0.00001);
    }
    @Test void maskUpgradeReplacesAndSuppressesOnlyOneKeyPulseWithoutRefreshingDeadline() {
        var s=new EchoEffectState(); assertTrue(s.disguise(new Mask("base",1,600,0),0));
        assertFalse(s.disguise(new Mask("base",1,700,0),1)); assertFalse(s.suppressKeyPulse(1));
        assertTrue(s.disguise(new Mask("up",2,901,1),1)); assertFalse(s.disguise(new Mask("base",1,800,0),2));
        assertTrue(s.suppressKeyPulse(2)); assertFalse(s.suppressKeyPulse(3)); assertEquals(901,s.mask(3).orElseThrow().until());
        assertTrue(s.mask(900).isPresent()); assertTrue(s.mask(901).isEmpty());
    }
    @Test void effectiveAttackOrHitBreaksWholeMaskAndClearAlsoRemovesIt() {
        var s=new EchoEffectState(); s.disguise(new Mask("up",2,900,1),0); s.breakMask();
        assertTrue(s.mask(1).isEmpty()); assertFalse(s.suppressKeyPulse(1));
        s.disguise(new Mask("up",2,900,1),1); s.clear(); assertTrue(s.mask(2).isEmpty());
    }
    @Test void cooldownAndClearNeverResetOnRepeatedRead() {
        var s=new EchoEffectState(); s.cooldown("false_submit",300);
        assertFalse(s.ready("false_submit",299)); assertTrue(s.ready("false_submit",300));
        s.add(Marker.TRADE,new Mark("pact",1200,2,0,false,16),0);
        s.boost(new Boost("dash",2,100,0.35),0); s.clear();
        assertTrue(s.mark(Marker.TRADE,0).isEmpty()); assertTrue(s.mobility(0).isEmpty()); assertTrue(s.ready("false_submit",0));
    }
    @Test void openingCargoAndSlowShareFinalHalfSpeedFloor() {
        var s=new EchoEffectState(); s.slow(new Boost("hunt",2,100,0.1),0);
        assertEquals(-0.5,s.speedDelta(0,0.1,true),0.00001);
        s.boost(new Boost("dash",2,100,0.35),0);
        assertEquals(-0.425,s.speedDelta(0,0.1,true),0.00001);
        assertEquals(0.15,s.speedDelta(0,0.1,false),0.00001);
    }
}
