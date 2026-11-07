package io.wifi.srepackcompat.voice;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class VoicechatReconnectBackoffTest {
    private static long seconds(long value) {
        return TimeUnit.SECONDS.toNanos(value);
    }

    @Test
    void neverRetriesWithoutPreviousSuccess() {
        var backoff = new VoicechatReconnectBackoff();
        assertFalse(backoff.shouldRetry(0));
        assertFalse(backoff.shouldRetry(seconds(1000)));
    }

    @Test
    void retriesAtFiveTenTwentyThenThirtyForever() {
        var backoff = new VoicechatReconnectBackoff();
        backoff.connected();
        assertFalse(backoff.shouldRetry(0));
        for (long deadline : new long[]{5, 15, 35, 65, 95, 125, 155}) {
            assertFalse(backoff.shouldRetry(seconds(deadline) - 1));
            assertTrue(backoff.shouldRetry(seconds(deadline)));
            assertFalse(backoff.shouldRetry(seconds(deadline)));
        }
    }

    @Test
    void successResetsToFiveSeconds() {
        var backoff = new VoicechatReconnectBackoff();
        backoff.connected();
        assertFalse(backoff.shouldRetry(0));
        assertTrue(backoff.shouldRetry(seconds(5)));
        backoff.connected();
        assertFalse(backoff.shouldRetry(seconds(6)));
        assertFalse(backoff.shouldRetry(seconds(11) - 1));
        assertTrue(backoff.shouldRetry(seconds(11)));
    }

    @Test
    void logoutOrServerChangeRequiresNewSuccess() {
        var backoff = new VoicechatReconnectBackoff();
        backoff.connected();
        assertFalse(backoff.shouldRetry(0));
        backoff.reset();
        assertFalse(backoff.shouldRetry(seconds(5)));
        assertFalse(backoff.shouldRetry(seconds(500)));
    }

    @Test
    void disabledPeriodDoesNotConsumeRetryOrCatchUp() {
        var backoff = new VoicechatReconnectBackoff();
        backoff.connected();
        assertFalse(backoff.shouldRetry(0));
        assertTrue(backoff.shouldRetry(seconds(5)));
        backoff.pause();
        assertFalse(backoff.shouldRetry(seconds(100)));
        assertFalse(backoff.shouldRetry(seconds(105) - 1));
        assertTrue(backoff.shouldRetry(seconds(105)));
    }

    @Test
    void lateTickOnlyMakesOneAttemptAndUsesCurrentTime() {
        var backoff = new VoicechatReconnectBackoff();
        backoff.connected();
        assertFalse(backoff.shouldRetry(0));
        assertTrue(backoff.shouldRetry(seconds(100)));
        assertFalse(backoff.shouldRetry(seconds(100)));
        assertFalse(backoff.shouldRetry(seconds(110) - 1));
        assertTrue(backoff.shouldRetry(seconds(110)));
    }

    @Test
    void monotonicClockWrapIsSafe() {
        var backoff = new VoicechatReconnectBackoff();
        backoff.connected();
        long start = Long.MAX_VALUE - seconds(1);
        assertFalse(backoff.shouldRetry(start));
        assertFalse(backoff.shouldRetry(start + seconds(5) - 1));
        assertTrue(backoff.shouldRetry(start + seconds(5)));
    }
}
