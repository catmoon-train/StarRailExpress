package org.agmas.noellesroles.game.modes.echo.result;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EchoResultSnapshotTest {
    @TempDir Path directory;

    @Test void survivesMutableSourceClearAndOfflineWinner() {
        UUID player = UUID.randomUUID();
        List<EchoResultSnapshot.PlayerResult> source = new ArrayList<>();
        source.add(new EchoResultSnapshot.PlayerResult(player, "carrier", EchoFaction.CARRIER,
                EchoLifeState.ELIMINATED, null, false, false, true));
        EchoResultSnapshot result = new EchoResultSnapshot(UUID.randomUUID(), "station",
                EchoResultSnapshot.Outcome.CARRIERS, "ESCAPE_QUOTA", 80, true, source);
        source.clear();
        assertEquals(1, result.players().size());
        assertTrue(result.didWin(player));
        assertThrows(UnsupportedOperationException.class, () -> result.players().clear());
    }

    @Test void rejectsDuplicateParticipantsAndVoidWinners() {
        var player = new EchoResultSnapshot.PlayerResult(UUID.randomUUID(), "carrier", EchoFaction.CARRIER,
                EchoLifeState.HEALTHY, null, false, true, true);
        assertThrows(IllegalArgumentException.class, () -> new EchoResultSnapshot(UUID.randomUUID(), "station",
                EchoResultSnapshot.Outcome.CARRIERS, "ESCAPE_QUOTA", 1, true, List.of(player, player)));
        assertThrows(IllegalArgumentException.class, () -> new EchoResultSnapshot(UUID.randomUUID(), "station",
                EchoResultSnapshot.Outcome.VOID, "ABORT", 1, true, List.of(player)));
    }

    @Test void reportWrittenOnceWithoutTemporaryArtifacts() throws Exception {
        UUID id = UUID.randomUUID();
        var first = new EchoResultSnapshot(id, "station", EchoResultSnapshot.Outcome.VOID, "ABORT", 1, true, List.of());
        EchoMatchReportStore.save(directory, first);
        Path file = directory.resolve("echo_freight/results/" + id + ".json");
        String contents = Files.readString(file);
        EchoMatchReportStore.save(directory, new EchoResultSnapshot(id, "station", EchoResultSnapshot.Outcome.VOID,
                "CHANGED", 99, true, List.of()));
        assertEquals(contents, Files.readString(file));
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }
}
