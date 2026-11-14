package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SackGhostVerdictsTest {
    @Test void hitsOnlyMarkAtZeroSanAndNeverMarkTwice() {
        var verdicts = new SackGhostVerdicts();
        UUID target = UUID.randomUUID();
        assertFalse(verdicts.markAfterHit(target, .8));
        assertFalse(verdicts.markAfterHit(target, .0001));
        assertFalse(verdicts.markAfterHit(target, Double.NaN));
        assertFalse(verdicts.markAfterHit(target, Double.NEGATIVE_INFINITY));
        assertFalse(verdicts.markAfterHit(null, 0));
        assertTrue(verdicts.markAfterHit(target, 0));
        assertFalse(verdicts.markAfterHit(target, 0));
        assertEquals(1, verdicts.size());
    }

    @Test void naturalEndReturnsVictimsOnceThenConsumesAllMarks() {
        var verdicts = new SackGhostVerdicts();
        UUID target = UUID.randomUUID();
        assertTrue(verdicts.markAfterHit(target, 0));
        assertEquals(List.of(target), verdicts.finish(true));
        assertEquals(0, verdicts.size());
        assertTrue(verdicts.finish(true).isEmpty());
    }

    @Test void interruptionCancelsMarksRatherThanExecutingThem() {
        var verdicts = new SackGhostVerdicts();
        verdicts.markAfterHit(UUID.randomUUID(), 0);
        assertTrue(verdicts.finish(false).isEmpty());
        assertTrue(verdicts.finish(true).isEmpty());
    }

    @Test void cleansingRescuesOneVictimWithoutClearingOtherMarks() {
        var verdicts = new SackGhostVerdicts();
        UUID rescued = UUID.randomUUID(), other = UUID.randomUUID();
        verdicts.markAfterHit(rescued, 0);
        verdicts.markAfterHit(other, 0);
        assertTrue(verdicts.cleanse(rescued));
        assertFalse(verdicts.cleanse(rescued));
        assertEquals(List.of(other), verdicts.finish(true));
    }

    @Test void cleansedVictimCanOnlyBeRemarkedByAnotherZeroSanHit() {
        var verdicts = new SackGhostVerdicts();
        UUID target = UUID.randomUUID();
        verdicts.markAfterHit(target, 0);
        verdicts.cleanse(target);
        assertFalse(verdicts.markAfterHit(target, .2));
        assertTrue(verdicts.markAfterHit(target, 0));
    }

    @Test void syncRestorationIsBoundedDeduplicatedAndSnapshotsAreImmutable() {
        var verdicts = new SackGhostVerdicts();
        var targets = new ArrayList<UUID>();
        for (int i = 0; i < 200; i++) targets.add(UUID.randomUUID());
        targets.add(null);
        verdicts.restore(targets);
        assertEquals(128, verdicts.size());
        assertThrows(UnsupportedOperationException.class, () -> verdicts.snapshot().clear());
        UUID target = UUID.randomUUID();
        verdicts.restore(List.of(target, target));
        assertEquals(1, verdicts.size());
        verdicts.clear();
        assertTrue(verdicts.snapshot().isEmpty());
    }

    @Test void virtualHealthTargetsMarkOnlyAtZeroVirtualHealthAndShareOneMarkSlot() {
        var verdicts = new SackGhostVerdicts();
        UUID target = UUID.randomUUID();
        assertFalse(verdicts.markAfterVirtualHit(target, 4));
        assertFalse(verdicts.markAfterVirtualHit(target, 1));
        assertFalse(verdicts.markAfterVirtualHit(null, 0));
        assertTrue(verdicts.markAfterVirtualHit(target, 0));
        assertEquals(1, verdicts.size());
        assertFalse(verdicts.markAfterVirtualHit(target, 0));
        assertFalse(verdicts.markAfterHit(target, 0));
        assertEquals(List.of(target), verdicts.finish(true));
    }
}
