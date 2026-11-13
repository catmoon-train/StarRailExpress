package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingAccessPolicyTest {
    /** 开放到局域网不再是拒绝条件：集成服主人仍需是训练会话标记、非专服、同维度的请求者。 */
    @Test void onlyLocalOwnerInMarkedWorldHasAccess() {
        for (int mask = 0; mask < 16; mask++) {
            boolean marked = (mask & 1) != 0, dedicated = (mask & 2) != 0;
            boolean owner = (mask & 4) != 0, sameWorld = (mask & 8) != 0;
            assertEquals(mask == 13, TrainingAccessPolicy.permits(marked, dedicated, owner, sameWorld), "mask=" + mask);
        }
    }
    @Test void localTrainingOutlivesPublishingToLan() {
        assertTrue(TrainingAccessPolicy.permits(true, false, true, true));
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
