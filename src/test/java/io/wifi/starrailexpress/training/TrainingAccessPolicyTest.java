package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingAccessPolicyTest {
    @Test void onlyUnpublishedLocalOwnerInMarkedWorldHasAccess() {
        for (int mask = 0; mask < 32; mask++) {
            boolean marked = (mask & 1) != 0, dedicated = (mask & 2) != 0;
            boolean published = (mask & 4) != 0, owner = (mask & 8) != 0, sameWorld = (mask & 16) != 0;
            assertEquals(mask == 25, TrainingAccessPolicy.permits(marked, dedicated, published, owner, sameWorld), "mask=" + mask);
        }
    }
    @Test void dedicatedRequiresEnabledBackendExclusiveLeaseOwnerAndMatchingWorld() {
        for (int mask = 0; mask < 16; mask++) {
            assertEquals(mask == 15, TrainingAccessPolicy.permitsDedicated((mask & 1) != 0, (mask & 2) != 0,
                    (mask & 4) != 0, (mask & 8) != 0), "mask=" + mask);
        }
    }
    @Test void requestsAreBoundedAndRecoverNextSecond() {
        var limiter = new TrainingRequestLimiter();
        for (int i = 0; i < 10; i++) assertTrue(limiter.allow(100));
        assertFalse(limiter.allow(100)); assertFalse(limiter.allow(119));
        assertTrue(limiter.allow(120));
    }
}
