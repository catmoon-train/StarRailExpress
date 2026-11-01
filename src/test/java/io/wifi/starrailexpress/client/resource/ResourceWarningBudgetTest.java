package io.wifi.starrailexpress.client.resource;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceWarningBudgetTest {
    @Test void millionVariantsProduceOnlyThreeDetailsAndOneSummary() {
        var budget = new ResourceWarningBudget<String>(3);
        int details = 0;
        for (int i = 0; i < 1_000_000; i++) {
            if (budget.allow("amendments:blockstates/wall_soul_lantern.json")) details++;
        }
        Map<String, Long> summary = new LinkedHashMap<>();
        budget.drain(summary::put);
        assertEquals(3, details);
        assertEquals(Map.of("amendments:blockstates/wall_soul_lantern.json", 999_997L), summary);
    }

    @Test void resourcesHaveIndependentBudgets() {
        var budget = new ResourceWarningBudget<String>(1);
        assertTrue(budget.allow("one"));
        assertFalse(budget.allow("one"));
        assertTrue(budget.allow("two"));
        Map<String, Long> summary = new LinkedHashMap<>();
        budget.drain(summary::put);
        assertEquals(Map.of("one", 1L), summary);
    }

    @Test void eachReloadStartsFreshAndDoesNotRepeatSummary() {
        var budget = new ResourceWarningBudget<String>(1);
        budget.allow("one");
        budget.allow("one");
        budget.drain((id, count) -> assertEquals(1L, count));
        budget.drain((id, count) -> fail("duplicate summary"));
        assertTrue(budget.allow("one"));
    }

    @Test void zeroSampleBudgetAndInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceWarningBudget<>(-1));
        var budget = new ResourceWarningBudget<String>(0);
        assertFalse(budget.allow("one"));
        budget.drain((id, count) -> assertEquals(1L, count));
    }
}
