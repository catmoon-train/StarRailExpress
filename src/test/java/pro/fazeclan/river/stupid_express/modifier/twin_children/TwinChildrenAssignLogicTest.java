package pro.fazeclan.river.stupid_express.modifier.twin_children;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwinChildrenAssignLogicTest {

    @Test
    void onlyInnocentsReceiveTwinChildren() {
        assertTrue(TwinChildrenAssignLogic.canReceive(TwinChildrenAssignLogic.Faction.INNOCENT));
        assertFalse(TwinChildrenAssignLogic.canReceive(TwinChildrenAssignLogic.Faction.KILLER));
        assertFalse(TwinChildrenAssignLogic.canReceive(TwinChildrenAssignLogic.Faction.INDEPENDENT_NEUTRAL));
    }
}
