package org.agmas.noellesroles.game.modes.echo.inventory;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger.Location.*;

class EchoResourceLedgerTest {
    @Test void terminalCannotReviveOrReregisterAndForeignMatchCannotWrite() {
        UUID match = UUID.randomUUID(), id = UUID.randomUUID();
        var ledger = new EchoResourceLedger(match);
        var source = ground(UUID.randomUUID());
        ledger.register(match,id,source);
        assertThrows(IllegalStateException.class, () -> ledger.register(match,id,source));
        assertThrows(IllegalArgumentException.class, () -> ledger.move(UUID.randomUUID(),id,source,consumed()));
        ledger.move(match,id,source,consumed());
        assertThrows(IllegalStateException.class, () -> ledger.move(match,id,consumed(),source));
        assertThrows(IllegalStateException.class, () -> ledger.register(match,id,source));
        assertThrows(UnsupportedOperationException.class, () -> ledger.snapshot().clear());
    }

    @Test void batchValidatesEverythingBeforeApplyingAndCanSwapFullContainers() {
        UUID match = UUID.randomUUID(), owner = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var ledger = new EchoResourceLedger(match);
        var left = cargo(owner,0);
        var right = cargo(owner,1);
        ledger.register(match,a,left);
        ledger.register(match,b,right);
        var before = ledger.snapshot();
        assertThrows(IllegalStateException.class, () -> ledger.batchMove(match,List.of(
                new EchoResourceLedger.Move(a,left,ground(UUID.randomUUID())),
                new EchoResourceLedger.Move(b,left,consumed()))));
        assertEquals(before,ledger.snapshot());
        assertThrows(IllegalStateException.class, () -> ledger.move(match,a,left,right));
        assertEquals(before,ledger.snapshot());
        ledger.batchMove(match,List.of(new EchoResourceLedger.Move(a,left,right),new EchoResourceLedger.Move(b,right,left)));
        assertEquals(right,ledger.find(a).orElseThrow());
        assertEquals(left,ledger.find(b).orElseThrow());
        assertThrows(IllegalStateException.class, () -> ledger.batchMove(match,List.of(
                new EchoResourceLedger.Move(a,right,left),new EchoResourceLedger.Move(a,right,left))));
    }

    @Test void randomizedTransfersKeepOneLocationPerInstanceAndBoundTwoPackSlots() {
        UUID match = UUID.randomUUID(), owner = UUID.randomUUID();
        var ledger = new EchoResourceLedger(match);
        List<UUID> ids = new ArrayList<>();
        Random random = new Random(1);
        for (int i=0;i<50;i++) {
            UUID id = new UUID(0,i);
            ids.add(id);
            ledger.register(match,id,ground(UUID.randomUUID()));
        }
        for (int i=0;i<1000;i++) {
            UUID id = ids.get(random.nextInt(ids.size()));
            var current = ledger.find(id).orElseThrow();
            ledger.move(match,id,current,ground(UUID.randomUUID()));
            assertEquals(50,ledger.snapshot().size());
            assertEquals(50,new HashSet<>(ledger.snapshot().values()).size());
        }
        ledger.move(match,ids.get(0),ledger.find(ids.get(0)).orElseThrow(),inventory(owner,0));
        ledger.move(match,ids.get(1),ledger.find(ids.get(1)).orElseThrow(),inventory(owner,0));
        assertThrows(IllegalStateException.class, () -> ledger.move(match,ids.get(2),
                ledger.find(ids.get(2)).orElseThrow(),inventory(owner,0)));
    }
}
