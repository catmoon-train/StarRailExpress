package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.stats.PlayerStats;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TrainingStatsSandboxTest {
    @Test void killsDeathsAndTimeCannotMutateOfficialObject() {
        UUID id = UUID.randomUUID();
        PlayerStats official = new PlayerStats(id);
        official.incrementTotalKills();
        TrainingStatsSandbox sandbox = new TrainingStatsSandbox();
        PlayerStats training = sandbox.get(id);
        training.incrementTotalKills();
        training.incrementTotalKills();
        training.incrementTotalDeaths();
        training.addPlayTime(2400);
        assertNotSame(official, training);
        assertSame(training, sandbox.get(id));
        assertEquals(1, official.getTotalKills());
        assertEquals(0, official.getTotalDeaths());
        assertEquals(0, official.getTotalPlayTime());
        assertEquals(2, training.getTotalKills());
        assertEquals(1, training.getTotalDeaths());
        assertEquals(2400, training.getTotalPlayTime());
    }

    @Test void discardIsIdempotentAndDoesNotCarryAcrossSessions() {
        UUID id = UUID.randomUUID();
        TrainingStatsSandbox sandbox = new TrainingStatsSandbox();
        PlayerStats previous = sandbox.get(id);
        previous.incrementTotalKills();
        sandbox.clear();
        sandbox.clear();
        PlayerStats next = sandbox.get(id);
        assertNotSame(previous, next);
        assertEquals(0, next.getTotalKills());
        assertEquals(1, previous.getTotalKills());
        assertNotSame(next, sandbox.get(UUID.randomUUID()));
    }
}
