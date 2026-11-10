package pro.fazeclan.river.stupid_express.role.arsonist;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ArsonistMomentVictimsTest {
    @Test void onlyDeathsInsideAnActiveMomentAreRecorded() {
        var deaths = new ArsonistMomentVictims();
        UUID owner = UUID.randomUUID(), victim = UUID.randomUUID();
        assertFalse(deaths.record(victim));
        deaths.begin(owner);
        assertFalse(deaths.record(owner));
        assertFalse(deaths.record(null));
        assertTrue(deaths.record(victim));
        assertTrue(deaths.contains(victim));
        assertFalse(deaths.record(victim));
        assertEquals(Set.of(victim), deaths.snapshot());
    }

    @Test void finishDrainsBeforeNestedDeathCallbacksAndCannotReviveTwice() {
        var deaths = new ArsonistMomentVictims();
        UUID victim = UUID.randomUUID();
        deaths.begin(UUID.randomUUID());
        deaths.record(victim);
        assertEquals(Set.of(victim), deaths.finish());
        assertFalse(deaths.record(UUID.randomUUID()));
        assertFalse(deaths.contains(victim));
        assertTrue(deaths.finish().isEmpty());
    }

    @Test void newMomentDoesNotInheritEarlierDeathsOrMutateSnapshots() {
        var deaths = new ArsonistMomentVictims();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        deaths.begin(UUID.randomUUID());
        deaths.record(first);
        Set<UUID> snapshot = deaths.snapshot();
        deaths.begin(UUID.randomUUID());
        deaths.record(second);
        assertEquals(Set.of(first), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(second));
        assertEquals(Set.of(second), deaths.finish());
    }
}
