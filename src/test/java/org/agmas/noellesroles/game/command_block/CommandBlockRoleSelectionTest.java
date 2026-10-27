package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CommandBlockRoleSelectionTest {
    private static final List<String> POOL = List.of("paper", "him", "dream");

    @Test
    void freshRoundCanRandomlyChooseAnyEnabledRole() {
        assertEquals(POOL, CommandBlockRoleSelection.candidates(POOL, Map.of(), Map.of()));
    }

    @Test
    void usedRolesStayExcludedEvenAfterTheyDie() {
        assertEquals(List.of("dream"), CommandBlockRoleSelection.candidates(POOL,
                Map.of("paper", 1, "him", 1), Map.of()));
    }

    @Test
    void afterPoolExhaustionPreferFewerLivingOfEquallyUsedRoles() {
        assertEquals(List.of("him", "dream"), CommandBlockRoleSelection.candidates(POOL,
                Map.of("paper", 1, "him", 1, "dream", 1), Map.of("paper", 1)));
    }

    @Test
    void repetitionsStayBalancedAcrossTheRound() {
        Map<String, Integer> uses = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            String role = CommandBlockRoleSelection.candidates(POOL, uses, Map.of()).getFirst();
            uses.merge(role, 1, Integer::sum);
            int min = POOL.stream().mapToInt(r -> uses.getOrDefault(r, 0)).min().orElseThrow();
            int max = POOL.stream().mapToInt(r -> uses.getOrDefault(r, 0)).max().orElseThrow();
            assertTrue(max - min <= 1);
        }
        assertEquals(Map.of("paper", 10, "him", 10, "dream", 10), uses);
    }

    @Test
    void existingUntrackedRolesAreNotConsideredUnseen() {
        assertEquals(List.of("him", "dream"), CommandBlockRoleSelection.candidates(POOL,
                Map.of(), Map.of("paper", 1)));
    }

    @Test
    void disabledRolesNeverReturnAndSingleRolePoolStillWorks() {
        assertEquals(List.of("him"), CommandBlockRoleSelection.candidates(List.of("him"),
                Map.of("him", 5), Map.of("him", 2)));
        assertTrue(CommandBlockRoleSelection.candidates(List.of(), Map.of(), Map.of()).isEmpty());
    }

    @Test
    void abortedPreviewDoesNotConsumeAChoiceAndNewRoundResetsHistory() {
        Map<String, Integer> uses = new HashMap<>(Map.of("paper", 1));
        List<String> first = CommandBlockRoleSelection.candidates(POOL, uses, Map.of());
        assertEquals(first, CommandBlockRoleSelection.candidates(POOL, uses, Map.of()));
        assertEquals(Map.of("paper", 1), uses);
        assertEquals(POOL, CommandBlockRoleSelection.candidates(POOL, Map.of(), Map.of()));
    }
}
