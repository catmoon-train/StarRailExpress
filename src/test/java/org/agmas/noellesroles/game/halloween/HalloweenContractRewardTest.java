package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenContractRewardTest {
    @Test void exactlyTwentyOfOneHundredRollsAward() {
        int successes = 0;
        for (int roll = 0; roll < 100; roll++) {
            if (new HalloweenCombatState().tryIssueContract(roll, false)) successes++;
        }
        assertEquals(20, successes);
        assertFalse(new HalloweenCombatState().tryIssueContract(-1, false));
        assertFalse(new HalloweenCombatState().tryIssueContract(100, false));
    }

    @Test void threeTasksPerRollAndFailureStartsAnotherThree() {
        var state = new HalloweenCombatState();
        UUID player = UUID.randomUUID();
        assertFalse(state.completeTask(player, 3));
        assertFalse(state.completeTask(player, 3));
        assertTrue(state.completeTask(player, 3));
        assertFalse(state.tryIssueContract(20, false));
        assertFalse(state.completeTask(player, 3));
        assertFalse(state.completeTask(player, 3));
        assertTrue(state.completeTask(player, 3));
        assertTrue(state.tryIssueContract(19, false));
    }

    @Test void occupiedIssuedAndAcceptedContractsPreventFurtherRewardsUntilNewRound() {
        var state = new HalloweenCombatState();
        assertFalse(state.tryIssueContract(0, true));
        assertFalse(state.hasIssuedContract());
        assertTrue(state.tryIssueContract(0, false));
        assertTrue(state.hasIssuedContract());
        state.disconnect(UUID.randomUUID());
        assertFalse(state.tryIssueContract(0, false));
        var accepted = new HalloweenCombatState();
        assertTrue(accepted.acceptPact(UUID.randomUUID()));
        assertFalse(accepted.tryIssueContract(0, false));
        assertTrue(new HalloweenCombatState().tryIssueContract(0, false));
    }

    @Test void productionChecksAllPlayersAndBorrowingDoesNotIssueExtraContracts() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles/game/halloween");
        String combat = Files.readString(root.resolve("HalloweenCombat.java"));
        assertTrue(combat.contains("completeTask(p.getUUID(), 3)"));
        assertTrue(combat.contains("tryIssueContract(p.getRandom().nextInt(100), contractOccupied(p))"));
        assertTrue(combat.contains("getPlayerList().getPlayers()"));
        assertTrue(combat.contains("state.hasIssuedContract()"));
        assertTrue(combat.contains("other.containerMenu.getCarried().is(HalloweenItems.SOUL_CONTRACT)"));
        assertTrue(combat.contains("entity instanceof ItemEntity item"));
        assertFalse(Files.readString(root.resolve("HalloweenLinkItems.java")).contains(".copy()"));
    }
}
