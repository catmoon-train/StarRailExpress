/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Minecraft-free helpers for Twin Children shared task completion. */
final class TwinChildrenTaskShareLogic {
    private TwinChildrenTaskShareLogic() {
    }

    /**
     * Completes the first non-skipped task and dismisses the rest.
     * Skipped tasks (e.g. manic) are left untouched.
     */
    static <T> void splitCompletable(Iterable<T> tasks, Predicate<T> skip, Consumer<T> complete,
            Consumer<T> dismiss) {
        boolean found = false;
        for (T task : tasks) {
            if (skip.test(task)) {
                continue;
            }
            if (!found) {
                complete.accept(task);
                found = true;
            } else {
                dismiss.accept(task);
            }
        }
    }
}
