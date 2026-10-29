package org.agmas.noellesroles.game.modes.echo.map;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EchoRoutePermitTest {
    private static final EchoRoutePermit.Stamp STAMP=new EchoRoutePermit.Stamp("dimension/map/hash/rules/offset/area/jump/roster",7);
    @Test void automaticPassCanBeConsumedExactlyOnce() {
        var p=new EchoRoutePermit(STAMP,0);
        p.complete(EchoRoutePermit.Result.PASSED,STAMP,10);
        assertEquals(EchoRoutePermit.Status.AUTHORIZED,p.status()); assertNull(p.nonce());
        assertTrue(p.consume(STAMP,11)); assertFalse(p.consume(STAMP,11));
        assertEquals(EchoRoutePermit.Status.FAILED,p.status());
    }
    @Test void manualIsNotPassAndCannotBeUsedWithoutConfirmation() {
        var p=new EchoRoutePermit(STAMP,0); p.complete(EchoRoutePermit.Result.MANUAL,STAMP,20);
        assertEquals(EchoRoutePermit.Status.MANUAL,p.status()); assertNotNull(p.nonce());
        assertFalse(p.consume(STAMP,21)); assertFalse(p.confirm(UUID.randomUUID(),STAMP,22));
    }
    @Test void validManualConfirmationAuthorizesOnlyOneStart() {
        var p=new EchoRoutePermit(STAMP,0); p.complete(EchoRoutePermit.Result.MANUAL,STAMP,20);
        var nonce=p.nonce(); assertTrue(p.confirm(nonce,STAMP,21)); assertNull(p.nonce());
        assertTrue(p.consume(STAMP,22)); assertFalse(p.confirm(nonce,STAMP,23));
    }
    @Test void knownFailureNeverCreatesManualOverride() {
        var p=new EchoRoutePermit(STAMP,0); p.complete(EchoRoutePermit.Result.FAILED,STAMP,1);
        assertNull(p.nonce()); p.complete(EchoRoutePermit.Result.MANUAL,STAMP,2);
        assertEquals(EchoRoutePermit.Status.FAILED,p.status()); assertFalse(p.confirm(UUID.randomUUID(),STAMP,3));
    }
    @Test void wrongNonceRevokesOriginalAndCannotBeReplayed() {
        var p=new EchoRoutePermit(STAMP,0); p.complete(EchoRoutePermit.Result.MANUAL,STAMP,1); var nonce=p.nonce();
        assertFalse(p.confirm(UUID.randomUUID(),STAMP,2)); assertFalse(p.confirm(nonce,STAMP,3)); assertNull(p.nonce());
    }
    @Test void changesDuringScanAndAfterConfirmationPermanentlyInvalidate() {
        for(var changed:new EchoRoutePermit.Stamp[]{new EchoRoutePermit.Stamp("other-map-or-rules-or-roster",7),new EchoRoutePermit.Stamp(STAMP.configuration(),8)}) {
            var scan=new EchoRoutePermit(STAMP,0); scan.complete(EchoRoutePermit.Result.PASSED,changed,1);
            assertEquals(EchoRoutePermit.Status.FAILED,scan.status()); assertFalse(scan.consume(STAMP,2));
            var manual=new EchoRoutePermit(STAMP,0); manual.complete(EchoRoutePermit.Result.MANUAL,STAMP,1);
            assertFalse(manual.confirm(manual.nonce(),changed,2)); assertFalse(manual.consume(STAMP,3));
            var ready=new EchoRoutePermit(STAMP,0); ready.complete(EchoRoutePermit.Result.PASSED,STAMP,1);
            assertFalse(ready.consume(changed,2)); assertFalse(ready.consume(STAMP,3));
        }
    }
    @Test void deadlinesAreExclusiveAndActivityDoesNotExtendThem() {
        var scanning=new EchoRoutePermit(STAMP,10); assertTrue(scanning.valid(STAMP,1209)); assertFalse(scanning.valid(STAMP,1210));
        var manual=new EchoRoutePermit(STAMP,0); manual.complete(EchoRoutePermit.Result.MANUAL,STAMP,20); var nonce=manual.nonce();
        assertTrue(manual.valid(STAMP,619)); assertFalse(manual.confirm(nonce,STAMP,620));
        var ready=new EchoRoutePermit(STAMP,0); ready.complete(EchoRoutePermit.Result.PASSED,STAMP,20);
        assertTrue(ready.valid(STAMP,119)); assertFalse(ready.consume(STAMP,120));
    }
    @Test void replayedCompletionDoesNotRefreshDeadlineOrReplaceToken() {
        var p=new EchoRoutePermit(STAMP,0); p.complete(EchoRoutePermit.Result.MANUAL,STAMP,1); var nonce=p.nonce();
        p.complete(EchoRoutePermit.Result.PASSED,STAMP,500); assertEquals(nonce,p.nonce()); assertEquals(EchoRoutePermit.Status.MANUAL,p.status());
        assertFalse(p.confirm(nonce,STAMP,601));
        var backwards=new EchoRoutePermit(STAMP,100); assertFalse(backwards.valid(STAMP,99));
    }
}
