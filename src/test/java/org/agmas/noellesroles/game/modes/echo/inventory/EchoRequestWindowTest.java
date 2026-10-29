package org.agmas.noellesroles.game.modes.echo.inventory;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EchoRequestWindowTest {
    @Test void rejectsWrongEpochProtocolAndReplaysCachedResultOnly() {
        var w=new EchoRequestWindow(); assertEquals("INVALID_SESSION",w.accept(1,UUID.randomUUID(),1,0,false));
        assertEquals("INVALID_SESSION",w.accept(2,w.epoch(),1,0,false)); assertEquals("",w.accept(1,w.epoch(),1,0,false));
        w.remember(1,"FULL"); assertEquals("DUPLICATE",w.accept(1,w.epoch(),1,0,false)); assertEquals("FULL",w.cached(1).orElseThrow());
        assertEquals("INVALID_SESSION",w.accept(1,w.epoch(),0,0,false));
    }
    @Test void boundedCacheDoesNotAllowEvictedRequestToRunAgain() {
        var w=new EchoRequestWindow();
        for(long n=1;n<=140;n++) { assertEquals("",w.accept(1,w.epoch(),n,n*20,false)); w.remember(n,""); }
        assertTrue(w.cached(1).isEmpty()); assertEquals("STALE_REQUEST",w.accept(1,w.epoch(),1,3000,false));
    }
    @Test void tokenBudgetAllowsTwentyBurstAndTenPerSecond() {
        var w=new EchoRequestWindow(); for(int n=1;n<=20;n++) assertEquals("",w.accept(1,w.epoch(),n,0,false));
        assertEquals("RATE_LIMIT",w.accept(1,w.epoch(),21,0,false));
        for(int n=22;n<32;n++) assertEquals("",w.accept(1,w.epoch(),n,20,false));
        assertEquals("RATE_LIMIT",w.accept(1,w.epoch(),32,20,false));
    }
    @Test void snapshotsHaveIndependentTwoPerSecondBudget() {
        var w=new EchoRequestWindow(); assertEquals("",w.accept(1,w.epoch(),1,0,true)); assertEquals("",w.accept(1,w.epoch(),2,0,true));
        assertEquals("RATE_LIMIT",w.accept(1,w.epoch(),3,0,true)); assertEquals("",w.accept(1,w.epoch(),4,0,false));
        assertEquals("",w.accept(1,w.epoch(),5,20,true));
    }
}
