package org.agmas.noellesroles.game.modes.echo.cards;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoSignalViewTest {
    @Test void everyDirectionKindRejectsTargetIdentity() {
        for(String kind:List.of("key_contract","salvage","cargo_pulse","key_pulse")) {
            assertThrows(IllegalArgumentException.class,() -> new EchoSignalView(kind,UUID.randomUUID(),0,20));
            assertNull(new EchoSignalView(kind,null,7,20).target());
        }
    }
    @Test void authorizedOutlinesKeepTheirTargetWhileSectorBoundsStayStrict() {
        UUID target=UUID.randomUUID(); assertEquals(target,new EchoSignalView("radar",target,0,20).target());
        assertEquals(target,new EchoSignalView("tracking",target,0,20).target());
        assertFalse(EchoSignalView.directionOnly("tracking"));
        assertThrows(IllegalArgumentException.class,() -> new EchoSignalView("key_pulse",null,8,20));
    }
}
