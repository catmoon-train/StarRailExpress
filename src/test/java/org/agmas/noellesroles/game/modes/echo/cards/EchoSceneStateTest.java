package org.agmas.noellesroles.game.modes.echo.cards;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoSceneState.*;

class EchoSceneStateTest {
    private Spec spec(Kind kind,int delay,int duration,int interval) { return new Spec(kind,new Point(0,0,0),0,delay,duration,interval); }
    @Test void ownerLimitRejectsWithoutReplacingOldScenes() {
        var s=new EchoSceneState(); var owner=UUID.randomUUID(); var dummy=spec(Kind.DUMMY,0,300,60);
        assertTrue(s.add(owner,List.of(dummy,dummy),0)); var before=s.all();
        assertFalse(s.add(owner,List.of(dummy),1)); assertEquals(before,s.all());
        assertTrue(s.add(owner,List.of(dummy),300)); assertEquals(1,s.all().size());
    }
    @Test void globalLimitIncludesAfterimagesAndNeverPartiallyAddsPair() {
        var s=new EchoSceneState(); var dummy=spec(Kind.IMAGE,0,300,-1);
        for(int i=0;i<11;i++) assertTrue(s.add(UUID.randomUUID(),List.of(dummy,dummy),0));
        assertTrue(s.add(UUID.randomUUID(),List.of(dummy),0)); assertEquals(23,s.all().size());
        assertFalse(s.add(UUID.randomUUID(),List.of(dummy,dummy),0)); assertEquals(23,s.all().size());
        assertTrue(s.add(UUID.randomUUID(),List.of(dummy),0));
        assertFalse(s.add(UUID.randomUUID(),List.of(dummy),0)); assertEquals(24,s.all().size());
    }
    @Test void delayedDispatchUsesFixedPointAndOnlyPlaysOnceAtStart() {
        var s=new EchoSceneState(); UUID owner=UUID.randomUUID();
        var second=new Spec(Kind.SUBMISSION,new Point(8,2,3),90,40,60,0);
        assertTrue(s.add(owner,List.of(spec(Kind.SUBMISSION,0,60,0),second),10));
        assertEquals(1,s.dueSounds(10).size()); assertTrue(s.dueSounds(10).isEmpty()); assertTrue(s.dueSounds(49).isEmpty());
        var due=s.dueSounds(50); assertEquals(1,due.size()); assertEquals(new Point(8,2,3),due.getFirst().point());
        assertTrue(s.dueSounds(50).isEmpty()); assertTrue(s.dueSounds(109).isEmpty()); s.expire(110); assertTrue(s.all().isEmpty());
    }
    @Test void repeatedPausedTickDoesNotReplayPeriodicSound() {
        var s=new EchoSceneState(); s.add(UUID.randomUUID(),List.of(spec(Kind.DUMMY,0,300,60)),0);
        assertEquals(1,s.dueSounds(0).size());
        for(int i=0;i<100;i++) assertTrue(s.dueSounds(0).isEmpty());
        assertTrue(s.dueSounds(59).isEmpty()); assertEquals(1,s.dueSounds(60).size()); assertTrue(s.dueSounds(60).isEmpty());
        assertTrue(s.dueSounds(300).isEmpty());
    }
    @Test void deceptionIsOnlySilentDummyFirstFiveSecondsAndTwentyBlocks() {
        var s=new EchoSceneState(); UUID owner=UUID.randomUUID();
        s.add(owner,List.of(spec(Kind.DUMMY,0,300,60)),0); assertTrue(s.deception(new Point(0,0,0),1).isEmpty());
        s.add(owner,List.of(spec(Kind.SILENT_DUMMY,0,400,-1)),10);
        assertTrue(s.deception(new Point(20,0,0),10).isPresent()); assertTrue(s.deception(new Point(20.01,0,0),10).isEmpty());
        assertTrue(s.deception(new Point(0,0,0),109).isPresent()); assertTrue(s.deception(new Point(0,0,0),110).isEmpty());
    }
    @Test void dismantleRejectsLightAndFutureScenesAndDepartureCancelsSecondSound() {
        var s=new EchoSceneState(); UUID owner=UUID.randomUUID();
        s.add(owner,List.of(spec(Kind.DUMMY,40,300,60),spec(Kind.SUBMISSION,0,60,0)),0);
        assertFalse(s.dismantle(s.all().get(0).id(),39)); assertFalse(s.dismantle(s.all().get(1).id(),0));
        assertTrue(s.dismantle(s.all().get(0).id(),40)); assertFalse(s.dismantle(s.all().get(0).id(),40));
        s.depart(owner); assertTrue(s.all().isEmpty()); assertTrue(s.dueSounds(100).isEmpty());
    }
    @Test void totalProjectionBoundRejectsWholePair() {
        var s=new EchoSceneState(); var light=spec(Kind.STALL,0,100,-1);
        for(int i=0;i<63;i++) assertTrue(s.add(UUID.randomUUID(),List.of(light),0));
        assertFalse(s.add(UUID.randomUUID(),List.of(light,light),0)); assertEquals(63,s.all().size());
        s.clear(); assertTrue(s.all().isEmpty());
    }
}
