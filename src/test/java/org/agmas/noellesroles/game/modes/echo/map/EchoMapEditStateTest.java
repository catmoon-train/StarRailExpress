package org.agmas.noellesroles.game.modes.echo.map;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EchoMapEditStateTest {
    private static final String HASH="a".repeat(64);
    private EchoMapEditState state() throws IOException {
        return new EchoMapEditState(new EchoMapEditState.Context("minecraft:overworld","season1/station",new EchoMapConfig.Vec(100,10,200)),EchoMapDraft.empty("season1/station"),0);
    }
    @Test void coordinatesSubtractRecordedOffsetExactlyOnceAndCornersAreHalfOpen() throws Exception {
        var state=state(); assertEquals(new EchoMapConfig.Vec(0.5,64,20.5),state.template(new EchoMapConfig.Vec(100.5,74,220.5)));
        assertThrows(IllegalArgumentException.class,state::selection);
        state.corner(true,new EchoMapConfig.Vec(104,76,222)); state.corner(false,new EchoMapConfig.Vec(100,74,220));
        assertEquals(new EchoMapConfig.Box(0,64,20,5,67,23),state.selection());
        state.corner(true,new EchoMapConfig.Vec(100,74,220)); assertEquals(new EchoMapConfig.Box(0,64,20,1,65,21),state.selection());
        assertThrows(IllegalArgumentException.class,() -> state.corner(true,new EchoMapConfig.Vec(0.5,0,0)));
    }
    @Test void contextAndTimeoutInvalidateButActivityDoesNotResetConfirmation() throws Exception {
        var state=state(); assertTrue(state.valid(state.context(),35999)); assertFalse(state.valid(state.context(),36000));
        state.touch(100); assertTrue(state.valid(state.context(),36000));
        assertFalse(state.valid(new EchoMapEditState.Context("minecraft:the_nether",state.context().mapId(),state.context().offset()),101));
        assertFalse(state.valid(new EchoMapEditState.Context(state.context().dimension(),"other",state.context().offset()),101));
        assertFalse(state.valid(new EchoMapEditState.Context(state.context().dimension(),state.context().mapId(),new EchoMapConfig.Vec(0,0,0)),101));
        var token=state.confirm("season1/station",HASH,100); state.touch(500);
        assertThrows(IOException.class,() -> state.consume(token.nonce(),token.mapId(),HASH,700));
    }
    @Test void matchingConfirmationCanCommitExactlyOnce() throws Exception {
        var state=state(); var token=state.confirm("season1/station",HASH,0);
        assertEquals(HASH,state.consume(token.nonce(),token.mapId(),HASH,599));
        assertThrows(IOException.class,() -> state.consume(token.nonce(),token.mapId(),HASH,599));
    }
    @Test void draftMutationOrFileMutationOrWrongNonceRejectsWithoutReusableToken() throws Exception {
        var state=state(); var changed=state.confirm("season1/station",HASH,0);
        state.draft().mark("spectator","view",new EchoMapConfig.Vec(0,75,0),0,0,6);
        assertThrows(IOException.class,() -> state.consume(changed.nonce(),changed.mapId(),HASH,1));
        var file=state.confirm("season1/station",HASH,2);
        assertThrows(IOException.class,() -> state.consume(file.nonce(),file.mapId(),"b".repeat(64),3));
        assertThrows(IOException.class,() -> state.consume(file.nonce(),file.mapId(),HASH,3));
        var wrong=state.confirm("season1/station",HASH,4);
        assertThrows(IOException.class,() -> state.consume(UUID.randomUUID(),wrong.mapId(),HASH,5));
        assertThrows(IOException.class,() -> state.consume(wrong.nonce(),wrong.mapId(),HASH,5));
    }
    @Test void mapIdAndReplacementConfirmationCannotBeReplayed() throws Exception {
        var state=state(); var old=state.confirm("season1/station",HASH,0); var current=state.confirm("season1/station",HASH,1);
        assertNotEquals(old.nonce(),current.nonce());
        assertThrows(IOException.class,() -> state.consume(old.nonce(),old.mapId(),HASH,2));
        var other=state.confirm("season1/station",HASH,3);
        assertThrows(IOException.class,() -> state.consume(other.nonce(),"season2/station",HASH,4));
        assertThrows(IOException.class,() -> state.confirm("../escape",HASH,5));
    }
    @Test void fractionalCopyOffsetsAreRejectedBeforeCreatingSession() throws Exception {
        assertThrows(IOException.class,() -> new EchoMapEditState(new EchoMapEditState.Context("world","station",new EchoMapConfig.Vec(0,0,0.5)),EchoMapDraft.empty("station"),0));
    }
}
