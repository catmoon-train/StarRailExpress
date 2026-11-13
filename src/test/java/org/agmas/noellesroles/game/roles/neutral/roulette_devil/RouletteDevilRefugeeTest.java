package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.agmas.noellesroles.role_data.neutral.RouletteDevilRoleData;
import org.junit.jupiter.api.Test;
import static org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilRefugeeState.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class RouletteDevilRefugeeTest {
    @Test void livingDevilIsSuspendedAndKeepsItsOriginalMode() {
        var state = new RouletteDevilRefugeeState();
        assertEquals(SUSPEND, state.update(true, true, true, false, 2));
        assertTrue(state.pending());
        assertEquals(2, state.returnGameType());
    }
    @Test void repeatedTicksDoNotResuspendSpectatorOrOverwriteReturnMode() {
        var state = new RouletteDevilRefugeeState();
        state.update(true, true, true, false, 0);
        for (int tick = 0; tick < 3000; tick++)
            assertEquals(NONE, state.update(true, true, false, true, 3));
        assertTrue(state.pending());
        assertEquals(0, state.returnGameType());
    }
    @Test void externalRevivalCannotEscapeTheActivePhase() {
        var state = new RouletteDevilRefugeeState();
        state.update(true, true, true, false, 0);
        assertEquals(SUSPEND, state.update(true, true, true, false, 2));
        assertEquals(0, state.returnGameType());
        assertTrue(state.pending());
    }
    @Test void alreadyDeadDevilGetsNoNewRevivalEntitlement() {
        var state = new RouletteDevilRefugeeState();
        assertEquals(NONE, state.update(true, true, false, true, 3));
        assertFalse(state.pending());
        assertEquals(NONE, state.update(true, false, false, true, 3));
    }
    @Test void waitsUntilTheWholeRefugeePhaseEndsThenResumesOnce() {
        var state = new RouletteDevilRefugeeState();
        state.update(true, true, true, false, 2);
        // 还有其他难民亡命徒存活时，阶段仍为 active，不能提前恢复。
        assertEquals(NONE, state.update(true, true, false, true, 3));
        assertEquals(RESUME, state.update(true, false, false, true, 3));
        assertFalse(state.pending());
        assertEquals(NONE, state.update(true, false, true, false, 2));
        assertEquals(NONE, state.update(true, false, false, true, 3));
    }
    @Test void gameEndClearsPendingWithoutRevivingAnyone() {
        var state = new RouletteDevilRefugeeState();
        state.update(true, true, true, false, 2);
        assertEquals(NONE, state.update(false, true, false, true, 3));
        assertFalse(state.pending());
        assertEquals(NONE, state.update(true, false, false, true, 3));
    }
    @Test void ordinaryPlayOrAnAlreadyRestoredPlayerNeedsNoModeChange() {
        var state = new RouletteDevilRefugeeState();
        assertEquals(NONE, state.update(true, false, true, false, 2));
        state.update(true, true, true, false, 2);
        assertEquals(NONE, state.update(true, false, true, false, 2));
        assertFalse(state.pending());
    }
    @Test void suppressionRoundTripPreservesProgressSeedsAndReturnAnchor() {
        var data = data();
        data.startingPlayers = 16; data.wins = 2; data.losses = 1;
        UUID target = new UUID(7, 8);
        data.seeds.put(target, 120L); data.notified.add(target);
        data.refugee.update(true, true, true, false, 0);
        data.refugeeReturn.putString("Dimension", "minecraft:overworld");
        data.refugeeReturn.putDouble("X", 14.5); data.refugeeReturn.putDouble("Y", 64);
        CompoundTag tag = new CompoundTag();
        data.writeToSyncNbt(tag, null);
        var restored = data();
        restored.readFromSyncNbt(tag, null);
        assertTrue(restored.refugee.pending());
        assertEquals(0, restored.refugee.returnGameType());
        assertEquals(data.refugeeReturn, restored.refugeeReturn);
        assertEquals(2, restored.wins); assertEquals(1, restored.losses);
        assertEquals(1, restored.lives()); assertEquals(4, restored.required());
        assertEquals(data.seeds, restored.seeds); assertEquals(data.notified, restored.notified);
        assertEquals(RESUME, restored.refugee.update(true, false, false, true, 3));
    }
    @Test void preSuppressionRewindNodeClearsTemporarySpectatorState() {
        var data = data(); data.wins = 1; data.losses = 1;
        CompoundTag before = new CompoundTag();
        data.writeToRewindNbt(before, null);
        data.refugee.update(true, true, true, false, 2);
        data.refugeeReturn.putDouble("X", 12);
        data.readFromRewindNbt(before, null);
        assertFalse(data.refugee.pending()); assertTrue(data.refugeeReturn.isEmpty());
        assertEquals(1, data.wins); assertEquals(1, data.losses);
        assertEquals(NONE, data.refugee.update(true, false, true, false, 2));
    }
    @Test void legacyNodesHaveNoSuppressionOrStaleAnchor() {
        var data = data();
        data.refugee.load(true, 0); data.refugeeReturn.putDouble("X", 99);
        data.readFromSyncNbt(new CompoundTag(), null);
        assertFalse(data.refugee.pending()); assertEquals(2, data.refugee.returnGameType());
        assertTrue(data.refugeeReturn.isEmpty());
    }
    @Test void refugeeHookReturnsPlayersBeforeCaptureAndSpectatesOnlyAfterAllCaptures() throws Exception {
        String source = Files.readString(Path.of("src/main/java/pro/fazeclan/river/stupid_express/modifier/refugee/cca/RefugeeComponent.java"));
        int start = source.indexOf("public void SavePlayersStats()");
        String save = source.substring(start, source.indexOf("public void LoadPlayersStats()", start));
        assertTrue(save.indexOf("cancelAllForRefugee(") < save.indexOf("PlayerStatsBeforeRefugee.SaveFromPlayer("));
        assertTrue(save.indexOf("cancelAllForRefugee(") < save.indexOf("TimeRewind.capture("));
        assertTrue(save.indexOf("TimeRewind.captureArea(") < save.indexOf(".suspendForRefugee(player)"));
    }
    @Test void refugeeCancellationDoesNotSettleOrChangeCountersAndRequestsAreBlocked() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/roles/neutral/roulette_devil/RouletteDevilSessions.java"));
        int start = source.indexOf("public static void cancelAllForRefugee(");
        String cancel = source.substring(start, source.indexOf("private static void close(", start));
        assertTrue(cancel.contains("close(s, server)"));
        assertFalse(cancel.contains("settle(")); assertFalse(cancel.contains("killPlayer("));
        assertFalse(cancel.contains(".wins")); assertFalse(cancel.contains(".losses"));
        int challenge = source.indexOf("public static boolean challenge(");
        assertTrue(source.indexOf("RouletteDevilRole.refugeeActive(demon)", challenge)
                < source.indexOf("RouletteDevilRoom.create(", challenge));
    }
    private static RouletteDevilRoleData data() {
        return new RouletteDevilRoleData(new RoleDataContext(null, null, null, null));
    }
}
