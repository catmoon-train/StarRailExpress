package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BackpackExternalRefreshTest {
    @Test void cleanCacheAcceptsTenWebsiteCardsWithoutReaddingOldBalance() {
        BackpackState local = BackpackState.createDefault();
        local.cards.put(FactionCardType.KILLER, 1);
        BackpackState remote = BackpackState.createDefault();
        remote.cards.put(FactionCardType.KILLER, 11);
        BackpackManager.mergeExternalRefresh(local, remote, Map.of(FactionCardType.KILLER, 1));
        assertEquals(11, remote.cards.get(FactionCardType.KILLER));
    }
    @Test void consumptionAndLocalGrantDuringReadArePreservedOnceWithCooldown() {
        BackpackState local = BackpackState.createDefault();
        local.cards.put(FactionCardType.KILLER, 0); // consumed one of the old cached cards
        local.cards.put(FactionCardType.CIVILIAN, 3); // local grant during async read
        local.markUsed(FactionCardType.KILLER, 200L);
        BackpackState remote = BackpackState.createDefault();
        remote.cards.put(FactionCardType.KILLER, 11);
        remote.cards.put(FactionCardType.CIVILIAN, 5);
        remote.markUsed(FactionCardType.KILLER, 100L);
        BackpackManager.mergeExternalRefresh(local, remote, Map.of(FactionCardType.KILLER, 1));
        assertEquals(10, remote.cards.get(FactionCardType.KILLER));
        assertEquals(8, remote.cards.get(FactionCardType.CIVILIAN));
        assertEquals(200L, remote.lastUsedAt(FactionCardType.KILLER));
    }
}
