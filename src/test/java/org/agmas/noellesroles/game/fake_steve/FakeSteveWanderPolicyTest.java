package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveWanderPolicyTest {
    @Test
    void taskPointsMustBeFarFromTheLastSpotAndTheRecentPlate() {
        assertTrue(FakeSteveWanderPolicy.isUsableTaskPoint(81.0D, false, false));
        assertFalse(FakeSteveWanderPolicy.isUsableTaskPoint(16.0D, false, false));
        assertFalse(FakeSteveWanderPolicy.isUsableTaskPoint(200.0D, true, false));
        assertFalse(FakeSteveWanderPolicy.isUsableTaskPoint(200.0D, false, true));
    }

    @Test
    void socialStandsStayAFewBlocksAwayFromAnotherPlayer() {
        assertTrue(FakeSteveWanderPolicy.isSocialStand(64.0D));
        assertFalse(FakeSteveWanderPolicy.isSocialStand(4.0D));
        assertFalse(FakeSteveWanderPolicy.isSocialStand(144.0D));
    }

    @Test
    void aMissingOrFailedRouteReselectsImmediately() {
        assertTrue(FakeSteveWanderPolicy.shouldReselectNow(true, 0, false));
        assertFalse(FakeSteveWanderPolicy.shouldReselectNow(false, 2, false));
        assertTrue(FakeSteveWanderPolicy.shouldReselectNow(false, 4, false));
        assertTrue(FakeSteveWanderPolicy.shouldReselectNow(false, 0, true));
        assertFalse(FakeSteveWanderPolicy.shouldReselectNow(false, 1, false));
    }
}
