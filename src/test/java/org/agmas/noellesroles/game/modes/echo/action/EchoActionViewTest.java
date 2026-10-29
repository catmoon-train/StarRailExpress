package org.agmas.noellesroles.game.modes.echo.action;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EchoActionViewTest {
    @Test void boundedProgressNeverPredictsSuccessAndDoesNotOverflow() {
        var view=new EchoActionView(UUID.randomUUID(),"CRYSTAL",100,80);
        assertEquals(0,view.progress(90)); assertEquals(500,view.progress(140));
        assertEquals(999,view.progress(180)); assertEquals(999,view.progress(Long.MAX_VALUE));
        assertEquals(40,view.remaining(140)); assertEquals(0,view.remaining(180));
    }
    @Test void unknownKindsInvalidDurationsAndOverflowingDeadlinesAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new EchoActionView(UUID.randomUUID(),"OTHER_PLAYER_UUID",0,20));
        assertThrows(IllegalArgumentException.class,() -> new EchoActionView(UUID.randomUUID(),"RESCUE",0,0));
        assertThrows(IllegalArgumentException.class,() -> new EchoActionView(UUID.randomUUID(),"RESCUE",0,72001));
        assertThrows(IllegalArgumentException.class,() -> new EchoActionView(UUID.randomUUID(),"RESCUE",Long.MAX_VALUE,20));
    }
}
