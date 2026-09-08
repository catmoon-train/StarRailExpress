/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwinChildrenTaskShareLogicTest {

    @Test
    void completesFirstTaskAndDismissesTheRest() {
        ArrayList<String> completed = new ArrayList<>();
        ArrayList<String> dismissed = new ArrayList<>();

        TwinChildrenTaskShareLogic.splitCompletable(List.of("eat", "sleep"), task -> false,
                completed::add, dismissed::add);

        assertEquals(List.of("eat"), completed);
        assertEquals(List.of("sleep"), dismissed);
    }

    @Test
    void skipsManicTasksAndLeavesThemUntouched() {
        ArrayList<String> completed = new ArrayList<>();
        ArrayList<String> dismissed = new ArrayList<>();

        TwinChildrenTaskShareLogic.splitCompletable(List.of("manic", "eat", "sleep"), "manic"::equals,
                completed::add, dismissed::add);

        assertEquals(List.of("eat"), completed);
        assertEquals(List.of("sleep"), dismissed);
    }

    @Test
    void doesNothingWhenOnlySkippedTasksRemain() {
        ArrayList<String> completed = new ArrayList<>();
        ArrayList<String> dismissed = new ArrayList<>();

        TwinChildrenTaskShareLogic.splitCompletable(List.of("manic"), "manic"::equals,
                completed::add, dismissed::add);

        assertTrue(completed.isEmpty());
        assertTrue(dismissed.isEmpty());
    }

    @Test
    void completesASingleTaskWithoutDismissingAnything() {
        ArrayList<String> completed = new ArrayList<>();
        ArrayList<String> dismissed = new ArrayList<>();

        TwinChildrenTaskShareLogic.splitCompletable(List.of("drink"), task -> false,
                completed::add, dismissed::add);

        assertEquals(List.of("drink"), completed);
        assertTrue(dismissed.isEmpty());
    }
}
