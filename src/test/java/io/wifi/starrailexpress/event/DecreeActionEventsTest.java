package io.wifi.starrailexpress.event;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DecreeActionEventsTest {
    @Test void weaponGateDefaultsToAllowAndStopsBeforeSideEffects() {
        var gate = ItemGameplayEvents.weaponGate();
        assertTrue(gate.invoker().allow(null, 12));
        List<Integer> calls = new ArrayList<>();
        gate.register((player, target) -> { calls.add(target); return true; });
        gate.register((player, target) -> { calls.add(-target); return false; });
        gate.register((player, target) -> { fail("拒绝后不应继续"); return true; });
        assertFalse(gate.invoker().allow(null, 12));
        assertEquals(List.of(12, -12), calls);
    }
    @Test void revivalGateAllowsUnrestrictedRoomAndPosition() {
        var gate = GameUtilityEvents.beforeReviveInvoker(new GameUtilityEvents.BeforeRevive[0]);
        assertTrue(gate.allow(null, null));
        assertTrue(gate.allow(null, new Vec3(1, 2, 3)));
    }
    @Test void revivalGatePreservesDestinationAndShortCircuits() {
        Vec3 destination = new Vec3(1, 2, 3);
        var gate = GameUtilityEvents.beforeReviveInvoker(new GameUtilityEvents.BeforeRevive[]{
                (player, pos) -> { assertSame(destination, pos); return false; },
                (player, pos) -> { fail("禁止复活后不得执行下一个监听器"); return true; }
        });
        assertFalse(gate.allow(null, destination));
    }
}
