package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.util.ShopEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShopEventsTest {
    @Test
    void absentExtensionsPreserveCoreDefaultsAndListIdentity() {
        assertTrue(ShopEvents.allowBuyInvoker(new ShopEvents.AllowBuy[0]).allow(null, null));
        assertFalse(ShopEvents.balanceInvoker(new ShopEvents.HandleBalance[0]).handle(null, 17));
        for (int amount : List.of(-100, 0, 100)) {
            assertEquals(amount, ShopEvents.incomeInvoker(new ShopEvents.ModifyIncome[0]).modify(null, amount));
        }
        List<ShopEntry> entries = List.of();
        assertSame(entries, ShopEvents.entriesInvoker(new ShopEvents.ModifyEntries[0]).modify(null, null, entries));
    }

    @Test
    void buyVetoStopsLaterListenersWithoutOverridingBasicChecks() {
        List<Integer> calls = new ArrayList<>();
        var invoker = ShopEvents.allowBuyInvoker(new ShopEvents.AllowBuy[] {
                (player, entry) -> { calls.add(1); return true; },
                (player, entry) -> { calls.add(2); return false; },
                (player, entry) -> { calls.add(3); return true; }
        });
        assertFalse(invoker.allow(null, null));
        assertEquals(List.of(1, 2), calls);
    }

    @Test
    void incomeTransformsReceivePreviousResultInRegistrationOrder() {
        List<Integer> inputs = new ArrayList<>();
        var invoker = ShopEvents.incomeInvoker(new ShopEvents.ModifyIncome[] {
                (player, amount) -> { inputs.add(amount); return amount / 2; },
                (player, amount) -> { inputs.add(amount); return amount * 3; }
        });
        assertEquals(114, invoker.modify(null, 77));
        assertEquals(List.of(77, 38), inputs);
    }

    @Test
    void entryTransformsChainWithoutCopyingOrMutatingSharedInputs() {
        List<ShopEntry> original = List.of();
        List<ShopEntry> first = Collections.singletonList(null);
        List<ShopEntry> second = new ArrayList<>();
        var invoker = ShopEvents.entriesInvoker(new ShopEvents.ModifyEntries[] {
                (role, player, entries) -> { assertSame(original, entries); return first; },
                (role, player, entries) -> { assertSame(first, entries); return second; }
        });
        assertSame(second, invoker.modify(null, null, original));
        assertTrue(original.isEmpty());
        assertEquals(1, first.size());
    }

    @Test
    void handledBalanceStopsLaterHandlersAndDefaultProcessing() {
        List<Integer> calls = new ArrayList<>();
        var invoker = ShopEvents.balanceInvoker(new ShopEvents.HandleBalance[] {
                (shop, amount) -> { calls.add(1); return false; },
                (shop, amount) -> { calls.add(2); return true; },
                (shop, amount) -> { calls.add(3); return false; }
        });
        assertTrue(invoker.handle(null, -9));
        assertEquals(List.of(1, 2), calls);
    }

    @Test
    void genericDispatchDoesNotSwallowListenerFailures() {
        var failure = new IllegalStateException("listener failure");
        var invoker = ShopEvents.incomeInvoker(new ShopEvents.ModifyIncome[] {
                (player, amount) -> { throw failure; }
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> invoker.modify(null, 20)));
    }
}
