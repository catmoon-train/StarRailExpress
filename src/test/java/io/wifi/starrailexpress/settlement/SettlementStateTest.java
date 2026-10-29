package io.wifi.starrailexpress.settlement;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SettlementStateTest {
    private SettlementState playing() {
        var state = new SettlementState();
        assertTrue(state.blackReady());
        assertTrue(state.ready());
        return state;
    }
    @Test void cannotStartBeforeBlackoutAndLoadHandshake() {
        var state = new SettlementState();
        assertFalse(state.ready());
        assertEquals(SettlementState.Phase.BLACKOUT,state.phase());
        assertTrue(state.blackReady());
        assertFalse(state.blackReady());
        assertTrue(state.ready());
        assertFalse(state.ready());
    }
    @Test void earlyOrRepeatedHoldPacketsCannotFastForward() {
        var state = playing();
        for (int tick=0; tick<100; tick++) {
            for (int n=0;n<100;n++) state.hold();
            assertFalse(state.tick(1000));
        }
        for (int tick=0; tick<29; tick++) {
            state.hold();
            assertFalse(state.tick(1000));
        }
        state.hold();
        assertTrue(state.tick(1000));
    }
    @Test void releaseAndExpiredLeaseResetProgress() {
        var state = playing();
        for (int n=0;n<100;n++) state.tick(1000);
        for (int n=0;n<20;n++) { state.hold(); assertFalse(state.tick(1000)); }
        state.release();
        for (int n=0;n<20;n++) { state.hold(); assertFalse(state.tick(1000)); }
        for (int n=0;n<12;n++) assertFalse(state.tick(1000));
        state.hold();
        assertFalse(state.tick(1000));
    }
    @Test void timeoutAndClosedAreTerminal() {
        var state = new SettlementState();
        for (int n=0;n<599;n++) assertFalse(state.tick(1000));
        assertTrue(state.tick(1000));
        state.close();
        assertFalse(state.blackReady()); assertFalse(state.ready());
        assertFalse(state.tick(1000));
    }
    @Test void naturalFinishCannotBeForgedEarly() {
        var state = playing();
        assertFalse(state.mayFinish(400));
        for (int n=0;n<398;n++) state.tick(400);
        assertTrue(state.mayFinish(400));
    }
    @Test void scoringMatchesFactionRulesAndKeepsEveryEvent() {
        UUID id = UUID.randomUUID();
        var killer = new SettlementSnapshot.Actor(id,"a","killer",0,true,false,true,3,99);
        var passenger = new SettlementSnapshot.Actor(id,"a","civilian",0,true,false,false,2,4);
        assertEquals(3,killer.score()); assertEquals(6,passenger.score());
        var events = java.util.stream.IntStream.range(0,2000)
                .mapToObj(n -> new SettlementSnapshot.Event(n,"TASK_COMPLETE",id,null,"{}",null,null,null)).toList();
        var snapshot = new SettlementSnapshot("minecraft:overworld",-100000,0,0,"{}",id,List.of(killer),events);
        assertEquals(2000,snapshot.events().size());
        assertEquals(320+2000*3+80,snapshot.durationTicks());
    }
    @Test void mvpIgnoresLosersAndTieBreakIsStable() {
        UUID low = new UUID(0,1), high = new UUID(0,2), loser = new UUID(0,3);
        var a = new SettlementSnapshot.Actor(low,"a","",0,true,false,false,1,4);
        var b = new SettlementSnapshot.Actor(high,"b","",0,true,false,true,5,0);
        var c = new SettlementSnapshot.Actor(loser,"c","",0,false,false,true,99,0);
        assertEquals(high,SettlementSnapshot.selectMvp(List.of(a,b,c)));
        assertEquals(high,SettlementSnapshot.selectMvp(List.of(c,b,a)));
        var tie = new SettlementSnapshot.Actor(low,"a","",0,true,false,true,5,0);
        assertEquals(low,SettlementSnapshot.selectMvp(List.of(b,tie)));
        assertNull(SettlementSnapshot.selectMvp(List.of(c)));
    }
}
