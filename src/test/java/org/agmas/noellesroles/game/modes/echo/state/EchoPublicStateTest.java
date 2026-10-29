package org.agmas.noellesroles.game.modes.echo.state;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoPublicStateTest {
    private EchoPublicState state(boolean paused,List<EchoPublicState.Beacon> beacons) {
        return new EchoPublicState(EchoPhase.OPERATIONS,100,1000,paused,0,3,true,150,false,0,false,beacons);
    }
    @Test void interpolationIsBoundedAndCannotSetCompletionFacts() {
        var view=state(false,List.of(new EchoPublicState.Beacon("b",3,3,999,false,140)));
        assertEquals(100,view.displayTick(-1)); assertEquals(105,view.displayTick(5));
        assertEquals(110,view.displayTick(10000)); assertEquals(990,view.displayRemaining(10000));
        assertFalse(view.beacons().getFirst().active()); assertFalse(view.evacuationOpen());
        assertEquals(1,EchoPublicState.seconds(120,110)); assertEquals(0,EchoPublicState.seconds(110,110));
    }
    @Test void pauseFreezesAllLocalTime() {
        var view=state(true,List.of());
        assertEquals(100,view.displayTick(1000)); assertEquals(1000,view.displayRemaining(1000));
    }
    @Test void boundedImmutablePublicProjectionRejectsInvalidValues() {
        var beacon=new EchoPublicState.Beacon("a",0,3,0,false,0);
        var input=new ArrayList<EchoPublicState.Beacon>(); input.add(beacon); var view=state(false,input); input.clear();
        assertEquals(1,view.beacons().size()); assertThrows(UnsupportedOperationException.class,() -> view.beacons().clear());
        assertThrows(IllegalArgumentException.class,() -> state(false,Collections.nCopies(4,beacon)));
        assertThrows(IllegalArgumentException.class,() -> state(false,List.of(beacon,beacon)));
        assertThrows(IllegalArgumentException.class,() -> new EchoPublicState.Beacon("a",4,3,0,false,0));
        assertThrows(IllegalArgumentException.class,() -> new EchoPublicState.Beacon("x".repeat(65),0,3,0,false,0));
        assertThrows(IllegalArgumentException.class,() -> new EchoPublicState.Beacon("a",0,3,1001,false,0));
    }
    @Test void beaconVisualsUseOnlyServerFactsAndPauseAndLockDeadlines() {
        var point=new EchoPublicState.Point(12,64,-5);
        var b=new EchoPublicState.Beacon("b",3,3,100,false,140,point,300);
        assertTrue(b.charging()); assertTrue(b.stalled(139)); assertFalse(b.stalled(140));
        assertFalse(b.working(139)); assertTrue(b.working(140));
        assertTrue(b.highlighted(299)); assertFalse(b.highlighted(300));
        var active=new EchoPublicState.Beacon("b",3,3,1000,true,0,point,0);
        assertFalse(active.charging()); assertFalse(active.working(10));
        assertThrows(IllegalArgumentException.class,() -> new EchoPublicState.Point(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,() -> new EchoPublicState.Point(30000001,0,0));
    }
    @Test void supportsFullThirtyTwoPlayerProjectionAndRejectsOverflow() {
        var full = new EchoPublicState(EchoPhase.EVACUATION,100,1200,false,21,14,
                true,0,true,0,false,List.of());
        assertEquals(21, full.escaped());
        assertEquals(14, full.quota());
        assertThrows(IllegalArgumentException.class, () -> new EchoPublicState(EchoPhase.EVACUATION,
                100,1200,false,33,14,true,0,true,0,false,List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EchoPublicState(EchoPhase.EVACUATION,
                100,1200,false,0,33,true,0,true,0,false,List.of()));
    }
}
