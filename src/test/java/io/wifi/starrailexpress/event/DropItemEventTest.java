package io.wifi.starrailexpress.event;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DropItemEventTest {
    @Test void emptyDropGateDefersToOriginalRoleRules() {
        assertEquals(InteractionResult.PASS, ItemGameplayEvents.dropGate().invoker().beforeDrop(null, null));
    }
    @Test void rejectionStopsListenersBeforeRoleOrWhitelistCanApprove() {
        var gate = ItemGameplayEvents.dropGate();
        List<Integer> calls = new ArrayList<>();
        gate.register((player, stack) -> { calls.add(1); return InteractionResult.PASS; });
        gate.register((player, stack) -> { calls.add(2); return InteractionResult.FAIL; });
        gate.register((player, stack) -> { fail("拒绝之后不得继续"); return InteractionResult.SUCCESS; });
        assertEquals(InteractionResult.FAIL, gate.invoker().beforeDrop(null, null));
        assertEquals(List.of(1, 2), calls);
    }
    @Test void firstNonPassResultIsPreservedWithoutBooleanConversion() {
        var gate = ItemGameplayEvents.dropGate();
        gate.register((player, stack) -> InteractionResult.SUCCESS);
        gate.register((player, stack) -> { fail("非PASS结果应短路"); return InteractionResult.FAIL; });
        assertEquals(InteractionResult.SUCCESS, gate.invoker().beforeDrop(null, null));
    }
    @Test void dispatcherQueriesEventBeforeRoleAndDisguiseNotifiesAfterCopy() throws Exception {
        Path java = Path.of("src/main/java/io/wifi/starrailexpress");
        String dispatcher = Files.readString(java.resolve("api/RoleMethodDispatcher.java"));
        int start = dispatcher.indexOf("public static InteractionResult callOnDropItem(");
        int event = dispatcher.indexOf("BEFORE_ROLE_DROP", start);
        assertTrue(event > start);
        assertTrue(event < dispatcher.indexOf("getCurrentRole(player)", start));
        String renderer = Files.readString(java.resolve("client/disguise/EntityDisguiseRenderer.java"));
        int copied = renderer.indexOf(".invoker().copied(player, dummy)");
        assertTrue(copied > renderer.lastIndexOf("dummy.tickCount = player.tickCount;"));
        assertTrue(copied > renderer.indexOf("private static void copyPlayerState("));
    }
}
