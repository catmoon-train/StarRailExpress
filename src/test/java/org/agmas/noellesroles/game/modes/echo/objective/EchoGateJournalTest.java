package org.agmas.noellesroles.game.modes.echo.objective;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoGateJournalTest {
    @TempDir Path world;
    private static class Blocks implements EchoGateJournal.Access {
        final Map<EchoGateJournal.Pos,String> blocks=new HashMap<>();
        int failAfter=Integer.MAX_VALUE,writes;
        public String read(EchoGateJournal.Pos pos) { return blocks.get(pos); }
        public void write(EchoGateJournal.Pos pos,String state) {
            if(writes++>=failAfter) throw new IllegalStateException("simulated crash");
            blocks.put(pos,state);
        }
    }
    @Test void normalRestoreIsIdempotentAndExternalEditsArePreserved() throws Exception {
        var pos=new EchoGateJournal.Pos(1,64,1); Blocks access=new Blocks(); access.blocks.put(pos,"closed");
        EchoGateJournal log=new EchoGateJournal(world,UUID.randomUUID(),"overworld","station",Map.of(pos,"closed"));
        log.apply(Map.of(pos,"air"),access); assertTrue(EchoGateJournal.pending(world,"overworld","station"));
        access.blocks.put(pos,"admin_edit"); assertFalse(log.restore(access)); assertEquals("admin_edit",access.read(pos));
        access.blocks.put(pos,"air"); assertTrue(log.restore(access)); assertEquals("closed",access.read(pos));
        assertTrue(log.restore(access)); assertFalse(EchoGateJournal.pending(world,"overworld","station"));
    }
    @Test void crashBetweenBlockWritesRestoresBothBeforeAndAfterStates() throws Exception {
        var a=new EchoGateJournal.Pos(1,64,1); var b=new EchoGateJournal.Pos(2,64,1);
        Blocks access=new Blocks(); access.blocks.put(a,"closed"); access.blocks.put(b,"closed");
        EchoGateJournal log=new EchoGateJournal(world,UUID.randomUUID(),"overworld","station",Map.of(a,"closed",b,"closed"));
        access.failAfter=1;
        assertThrows(IllegalStateException.class,() -> log.apply(Map.of(a,"air",b,"air"),access));
        access.failAfter=Integer.MAX_VALUE;
        assertTrue(EchoGateJournal.recover(world,"overworld",access).isEmpty());
        assertEquals("closed",access.read(a)); assertEquals("closed",access.read(b));
    }
    @Test void recoveryNeverWritesIntoAnotherDimension() throws Exception {
        var pos=new EchoGateJournal.Pos(1,64,1); Blocks access=new Blocks(); access.blocks.put(pos,"closed");
        EchoGateJournal log=new EchoGateJournal(world,UUID.randomUUID(),"overworld","station",Map.of(pos,"closed"));
        log.apply(Map.of(pos,"air"),access);
        assertTrue(EchoGateJournal.recover(world,"nether",access).isEmpty()); assertEquals("air",access.read(pos));
        assertTrue(EchoGateJournal.pending(world,"overworld","station"));
        assertTrue(EchoGateJournal.recover(world,"overworld",access).isEmpty()); assertEquals("closed",access.read(pos));
    }
    @Test void failedPreconditionWritesNeitherBlocksNorRecoveryFile() {
        var a=new EchoGateJournal.Pos(1,64,1); var b=new EchoGateJournal.Pos(2,64,1);
        Blocks access=new Blocks(); access.blocks.put(a,"closed"); access.blocks.put(b,"admin_edit");
        EchoGateJournal log=new EchoGateJournal(world,UUID.randomUUID(),"overworld","station",Map.of(a,"closed",b,"closed"));
        assertThrows(java.io.IOException.class,() -> log.apply(Map.of(a,"air",b,"air"),access));
        assertEquals(0,access.writes); assertEquals("closed",access.read(a));
        assertFalse(EchoGateJournal.pending(world,"overworld","station"));
    }
}
