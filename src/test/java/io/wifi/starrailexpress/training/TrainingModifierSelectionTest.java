package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TrainingModifierSelectionTest {
    @Test void legacyNullInvalidDuplicateAndOversizedSelectionsNormalizeSafely() {
        assertTrue(TrainingModifierSelection.normalize(null).isEmpty());
        var input = Arrays.asList(null, "", "bad id", "demo:fast", "demo:fast", "demo:small", "x".repeat(129));
        assertEquals(List.of("demo:fast", "demo:small"), TrainingModifierSelection.normalize(input));
        var many = IntStream.range(0, 100).mapToObj(i -> "demo:m" + i).toList();
        var selected = TrainingModifierSelection.normalize(many);
        assertEquals(16, selected.size());
        assertEquals(many.subList(0, 16), selected);
        assertEquals(selected, TrainingModifierSelection.normalize(selected));
    }

    @Test void repeatedAddRemoveAndClearAreIdempotentAndDoNotMutateInput() {
        var input = new ArrayList<>(List.of("demo:fast"));
        var added = edit(input, "add", "demo:small");
        assertEquals(List.of("demo:fast", "demo:small"), added);
        assertEquals(List.of("demo:fast"), input);
        assertEquals(added, edit(added, "add", "demo:small"));
        assertEquals(List.of("demo:small"), edit(added, "remove", "demo:fast"));
        assertEquals(added, edit(added, "remove", "demo:missing"));
        assertTrue(edit(added, "clear", "").isEmpty());
        assertEquals(List.of("demo:fast", "demo:small"), added);
    }

    @Test void rejectingRoleConflictLimitAndUnknownOperationKeepsOriginalSelection() {
        var input = new ArrayList<>(List.of("demo:fast"));
        var role = assertThrows(IllegalArgumentException.class, () -> TrainingModifierSelection.edit(input, "add", "demo:small", id -> false, (a,b) -> false));
        assertEquals("training.sre.error.modifier_role", role.getMessage());
        var conflict = assertThrows(IllegalArgumentException.class, () -> TrainingModifierSelection.edit(input, "add", "demo:slow", id -> true, (a,b) -> a.equals("demo:slow") && b.equals("demo:fast")));
        assertEquals("training.sre.error.modifier_role", conflict.getMessage());
        assertEquals(List.of("demo:fast"), input);
        var full = IntStream.range(0, 16).mapToObj(i -> "demo:m" + i).toList();
        var limit = assertThrows(IllegalArgumentException.class, () -> edit(full, "add", "demo:extra"));
        assertEquals("training.sre.error.modifier_limit", limit.getMessage());
        assertEquals(full, edit(full, "add", "demo:m0"));
        assertThrows(IllegalArgumentException.class, () -> edit(input, "execute", "demo:fast"));
        assertEquals(List.of("demo:fast"), input);
    }

    @Test void removalAndClearRemainAvailableWhenRoleNoLongerAllowsTheModifier() {
        var input = List.of("demo:fast");
        assertTrue(TrainingModifierSelection.edit(input, "remove", "demo:fast", id -> false, (a,b) -> true).isEmpty());
        assertTrue(TrainingModifierSelection.edit(input, "clear", "", id -> false, (a,b) -> true).isEmpty());
    }

    @Test void selectionCopiesDoNotLeakBetweenOwnersOrBotSlots() {
        var first = edit(List.of(), "add", "demo:fast");
        var second = TrainingModifierSelection.normalize(first);
        second.add("demo:small");
        assertEquals(List.of("demo:fast"), first);
        assertEquals(List.of("demo:fast", "demo:small"), second);
    }

    private List<String> edit(List<String> current, String operation, String id) {
        return TrainingModifierSelection.edit(current, operation, id, key -> true, (a,b) -> false);
    }
}
