package org.agmas.noellesroles.game.minigame.roulette;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DevilRouletteLifecycleTest {
    @Test void impactIsCommittedOnceAndResetCancelsPendingAction() {
        var gate = new RouletteActionGate();
        for (var phase : new RoulettePhase[] {RoulettePhase.SHOOT, RoulettePhase.ITEM, RoulettePhase.ADRENALINE}) {
            gate.begin(phase, 100);
            assertFalse(gate.claimImpact(100 + phase.impact() - 1));
            assertTrue(gate.claimImpact(100 + phase.impact()));
            assertFalse(gate.claimImpact(100 + phase.impact()));
            assertFalse(gate.claimImpact(1000));
            gate.begin(phase, 2000); gate.reset();
            assertFalse(gate.claimImpact(3000));
        }
    }
    @Test void settlementIsSingleCommitPerSessionAndNeverDuringAnAction() {
        var gate = new RouletteActionGate();
        gate.begin(RoulettePhase.SHOOT, 0); assertFalse(gate.claimSettlement());
        gate.begin(RoulettePhase.ENDING, 40); assertTrue(gate.claimSettlement());
        assertFalse(gate.claimSettlement());
        gate.begin(RoulettePhase.ENDING, 50); assertFalse(gate.claimSettlement());
        gate.reset(); gate.begin(RoulettePhase.ENDING, 100); assertTrue(gate.claimSettlement());
    }
    @Test void staleForgedAndOutOfTurnSelectionsAreRejected() {
        UUID session = UUID.randomUUID(), token = UUID.randomUUID(), owner = UUID.randomUUID();
        assertTrue(RouletteSelectionPolicy.authorized(session, UUID.fromString(session.toString()), token, token,
                owner, owner, true, true, true, true, true));
        assertFalse(RouletteSelectionPolicy.authorized(session, UUID.randomUUID(), token, token, owner, owner, true, true, true, true, true));
        assertFalse(RouletteSelectionPolicy.authorized(session, session, null, token, owner, owner, true, true, true, true, true));
        assertFalse(RouletteSelectionPolicy.authorized(session, session, token, UUID.randomUUID(), owner, owner, true, true, true, true, true));
        assertFalse(RouletteSelectionPolicy.authorized(session, session, token, token, owner, UUID.randomUUID(), true, true, true, true, true));
        for (int invalid = 0; invalid < 5; invalid++) {
            boolean[] flags = {true, true, true, true, true}; flags[invalid] = false;
            assertFalse(RouletteSelectionPolicy.authorized(session, session, token, token, owner, owner,
                    flags[0], flags[1], flags[2], flags[3], flags[4]));
        }
        for (RouletteItem kind : RouletteItem.values())
            assertEquals(kind != RouletteItem.ADRENALINE, RouletteSelectionPolicy.stealable(kind, true, true, true));
        assertFalse(RouletteSelectionPolicy.stealable(null, true, true, true));
        assertFalse(RouletteSelectionPolicy.stealable(RouletteItem.BEER, false, true, true));
        assertFalse(RouletteSelectionPolicy.stealable(RouletteItem.BEER, true, false, true));
        assertFalse(RouletteSelectionPolicy.stealable(RouletteItem.BEER, true, true, false));
    }
    @Test void supplyCapacityCountsExistingAndPendingWithoutDeletingLegacyExcess() {
        assertEquals(5, RouletteItems.grantCount(0, 0, 5));
        assertEquals(2, RouletteItems.grantCount(4, 2, 5));
        assertEquals(0, RouletteItems.grantCount(8, 0, 5));
        assertEquals(0, RouletteItems.grantCount(64, 0, 5));
        assertEquals(0, RouletteItems.grantCount(4, 4, 5));
    }
    @Test void lateChunkSnapshotsCannotReplaceNewerActionOrSession() {
        UUID session = UUID.randomUUID();
        var old = snapshot(session, 8, 100);
        assertFalse(snapshot(session, 7, 100).supersedes(old));
        assertTrue(snapshot(session, 9, 100).supersedes(old));
        assertFalse(snapshot(UUID.randomUUID(), 0, 0).supersedes(old));
        assertTrue(snapshot(UUID.randomUUID(), 0, 110).supersedes(old));
    }
    private RouletteView snapshot(UUID session, long sequence, long time) {
        UUID none = RouletteView.NONE;
        return new RouletteView(session, sequence, RoulettePhase.SHOOT, time, 28, none, none, none,
                "", "", 5, 5, 3, 3, 5, false, none, null, null, none, none, -1, 0,
                java.util.List.of(), java.util.List.of(), "", 30, none);
    }
    @Test void publicSerializationCannotContainPrivateRoundOrKnowledge() throws Exception {
        String view = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/minigame/roulette/RouletteView.java"));
        assertFalse(view.contains("knownShell")); assertFalse(view.contains("knownOffset"));
        assertFalse(view.contains("chamber")); assertFalse(view.contains("bulletList"));
        String table = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/content/block_entity/DevilRouletteTableEntity.java"));
        assertTrue(table.contains("actionGate.claimImpact(level.getGameTime())"));
        assertTrue(table.contains("actionGate.claimSettlement()"));
        assertTrue(table.contains("RouletteSelectionPolicy.authorized("));
        assertTrue(table.contains("RouletteSelectionPolicy.stealable("));
        String tag = table.substring(table.indexOf("getUpdateTag("), table.indexOf("getUpdatePacket()"));
        assertTrue(tag.contains("view.toTag()")); assertFalse(tag.contains("round()"));
        String reset = table.substring(table.indexOf("public void reset()"));
        assertTrue(reset.contains("actionGate.reset()")); assertFalse(reset.contains("Scheduler" + ".schedule"));
    }
}
