package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PumpkinHideoutInteractionGateTest {
    @Test void firstClickAtTickZeroIsAcceptedButDuplicatePacketsAreNot() {
        var gate = new PumpkinHideoutInteractionGate();
        UUID player = UUID.randomUUID();
        assertTrue(gate.tryUse(player, 0));
        assertFalse(gate.tryUse(player, 0));
        for (int tick = 1; tick < 5; tick++) assertFalse(gate.tryUse(player, tick));
        assertTrue(gate.tryUse(player, 5), "拒绝重复包不能延长冷却");
    }

    @Test void oneClickDoesNotToggleEnterExitAndExitDuplicatesDoNotReenter() {
        var gate = new PumpkinHideoutInteractionGate();
        UUID player = UUID.randomUUID();
        boolean inside = false;
        for (long tick : new long[]{100, 100, 101, 104}) {
            if (gate.tryUse(player, tick)) inside = !inside;
        }
        assertTrue(inside, "一次右键的重复包只能进入，不能马上退出");
        for (long tick : new long[]{105, 105, 106, 109}) {
            if (gate.tryUse(player, tick)) inside = !inside;
        }
        assertFalse(inside, "再次右键退出，重复包不能重新进入");
    }

    @Test void differentPlayersAreIndependentAndDisconnectClearsOnlyThatPlayer() {
        var gate = new PumpkinHideoutInteractionGate();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(gate.tryUse(first, 200));
        assertTrue(gate.tryUse(second, 200));
        gate.remove(first);
        assertTrue(gate.tryUse(first, 201));
        assertFalse(gate.tryUse(second, 201));
    }

    @Test void callbackChecksMainHandAndKnownHideoutBeforeGateAndGateBeforeToggle() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/PumpkinHideouts.java"));
        int gate = source.indexOf("state.interactions.tryUse(");
        assertTrue(gate > source.indexOf("hand != InteractionHand.MAIN_HAND"));
        assertTrue(gate > source.indexOf("if (hideout == null) return InteractionResult.FAIL;"));
        assertTrue(gate < source.indexOf("Guest guest = state.guests.get(player.getUUID());"));
        assertTrue(source.contains("state.interactions.remove(player.getUUID());"));
    }
}
