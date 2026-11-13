package org.agmas.noellesroles.game.roles.innocence.decree;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class DecreeRulesTest {
    @Test void completeFixedPriceDictionary() {
        assertEquals(16, Decree.values().length);
        assertArrayEquals(new int[]{275,300,100,100,150,200,125,140,150,175,175,80,100,140,125,125},
                Arrays.stream(Decree.values()).mapToInt(Decree::cost).toArray());
        assertEquals(16, new HashSet<>(Arrays.stream(Decree.values()).map(Decree::id).toList()).size());
        for (Decree decree : Decree.values()) assertSame(decree, Decree.byId(decree.id()));
        assertNull(Decree.byId("MURDER"));
        assertNull(Decree.byId("任意伪造律令"));
        assertNull(Decree.byId(null));
    }
    @Test void murderLastsSevenSecondsAllOthersEleven() {
        for (Decree decree : Decree.values()) {
            assertEquals(decree == Decree.MURDER ? 140 : 220, decree.durationTicks());
        }
    }
    @Test void exactSphericalBoundaryAndCasterExceptions() {
        for (Decree decree : Decree.values()) {
            assertTrue(decree.contains(225, false));
            assertFalse(decree.contains(225.00001, false));
            assertFalse(decree.contains(Double.NaN, false));
            assertFalse(decree.contains(Double.POSITIVE_INFINITY, false));
            assertFalse(decree.contains(-1, false));
            assertEquals(decree != Decree.LIGHT, decree.contains(0, true));
        }
    }
    @Test void delayedDamageIsNotRetroactivelyForbidden() {
        for (String reason : new String[]{"poison", "bleeding", "flamethrower_burned", "infection",
                "undead_infection", "wizard_fire_arrow", "grenade", "c4_explosion", "delayed_kill"}) {
            assertTrue(DecreeService.isDelayedDamage(reason), reason);
        }
        for (String reason : new String[]{"knife_stab", "gun_shot", "revolver_shot", "execute", "generic"}) {
            assertFalse(DecreeService.isDelayedDamage(reason), reason);
        }
    }
}
