package org.agmas.noellesroles.game.modes.echo.trade;

import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger.Location;

class EchoTradeStateTest {
    private final UUID match=UUID.randomUUID();
    private final EchoResourceLedger ledger=new EchoResourceLedger(match);
    private final EchoInventory a=new EchoInventory(UUID.randomUUID(),ledger),b=new EchoInventory(UUID.randomUUID(),ledger);
    private EchoResourceInstance card(String id) { return EchoResourceInstance.card(match,UUID.randomUUID(),EchoCardRegistry.require("noellesroles:echo/"+id),UUID.randomUUID()); }
    private EchoResourceInstance give(EchoInventory bag,String id) {
        var item=card(id); var from=Location.ground(UUID.randomUUID()); ledger.register(match,item.instanceId(),from); bag.receive(item,from,bag.revision()); return item;
    }
    private EchoTradeState editing() { var t=new EchoTradeState(a,b,400,3); t.accept(b.owner(),t.version(),0); return t; }
    private void quote(EchoTradeState t,EchoInventory bag,UUID...ids) { t.offer(bag.owner(),List.of(ids),t.version(),bag.revision(),0); }
    @Test void onlyRecipientAcceptsAndBothEmptyCannotConfirm() {
        var t=new EchoTradeState(a,b,400,3);
        assertThrows(IllegalStateException.class,() -> t.accept(a.owner(),t.version(),0));
        t.accept(b.owner(),t.version(),0); assertEquals("INVALID_OFFER",t.layoutError());
        assertThrows(IllegalStateException.class,() -> t.confirm(a.owner(),t.version(),a.revision(),0));
        assertFalse(t.confirmed(a.owner()));
    }
    @Test void fullBagsExchangeOnlyAfterBothConfirmSameVersionAndCommitOnce() {
        var ia=give(a,"sound_key"); var ib=give(b,"short_dash");
        for(int n=0;n<5;n++) { give(a,"short_dash"); give(b,"steady_flow"); }
        var t=editing(); quote(t,a,ia.instanceId()); quote(t,b,ib.instanceId());
        var locations=ledger.snapshot(); assertEquals("",t.layoutError());
        assertFalse(t.confirm(a.owner(),t.version(),a.revision(),0)); assertEquals(locations,ledger.snapshot());
        assertTrue(t.confirm(b.owner(),t.version(),b.revision(),0));
        assertEquals(EchoTradeState.Status.COMMITTED,t.status()); assertTrue(a.find(ib.instanceId()).isPresent()); assertTrue(b.find(ia.instanceId()).isPresent());
        assertEquals(6,a.items().size()); assertEquals(6,b.items().size()); assertFalse(a.locked(ia.instanceId()));
        assertEquals(List.of(ia),t.committed().orElseThrow().fromA());
        var done=ledger.snapshot(); assertThrows(IllegalStateException.class,() -> t.confirm(b.owner(),t.version(),b.revision(),0)); assertEquals(done,ledger.snapshot());
    }
    @Test void giftWorksButFullReceiverIsZeroMutation() {
        var ia=give(a,"short_dash"); var t=editing(); quote(t,a,ia.instanceId());
        assertFalse(t.confirm(a.owner(),t.version(),a.revision(),0)); assertTrue(t.confirm(b.owner(),t.version(),b.revision(),0));
        assertTrue(b.find(ia.instanceId()).isPresent()); assertTrue(t.committed().orElseThrow().fromB().isEmpty());
        var another=give(a,"short_dash"); for(int n=0;n<5;n++) give(b,"steady_flow");
        var full=editing(); quote(full,a,another.instanceId()); var old=ledger.snapshot();
        assertEquals("FULL",full.layoutError()); assertThrows(IllegalStateException.class,() -> full.confirm(a.owner(),full.version(),a.revision(),0)); assertEquals(old,ledger.snapshot());
        full.cancel(full.id()); assertFalse(a.locked(another.instanceId()));
    }
    @Test void offerChangeAndUnrelatedInventoryMutationClearBothConfirmations() {
        var ia=give(a,"short_dash"); var ia2=give(a,"steady_flow"); var ib=give(b,"short_dash");
        var t=editing(); quote(t,a,ia.instanceId()); quote(t,b,ib.instanceId());
        assertFalse(t.confirm(a.owner(),t.version(),a.revision(),0)); long old=t.version();
        quote(t,b); assertTrue(t.version()>old); assertFalse(t.confirmed(a.owner()));
        long staleOffer=old; assertThrows(IllegalStateException.class,() -> t.confirm(b.owner(),staleOffer,b.revision(),0));
        quote(t,b,ib.instanceId()); assertFalse(t.confirm(a.owner(),t.version(),a.revision(),0)); old=t.version();
        a.move(ia2.instanceId(),5,a.revision()); long stale=old;
        assertThrows(IllegalStateException.class,() -> t.confirm(b.owner(),stale,b.revision(),0)); assertFalse(t.confirmed(a.owner())); assertTrue(t.version()>old);
    }
    @Test void invalidQuoteDoesNotReleasePreviousOrBoundPackLock() {
        var ia=give(a,"short_dash"); var other=give(a,"steady_flow"); UUID binding=UUID.randomUUID(); a.lock(other.instanceId(),binding,a.revision());
        var t=editing(); quote(t,a,ia.instanceId()); var before=a.snapshot();
        assertThrows(IllegalStateException.class,() -> quote(t,a,other.instanceId())); assertEquals(before,a.snapshot());
        assertThrows(IllegalStateException.class,() -> quote(t,a,ia.instanceId(),ia.instanceId())); assertEquals(before,a.snapshot());
        t.cancel(t.id()); assertTrue(a.locked(other.instanceId())); assertFalse(a.locked(ia.instanceId()));
    }
    @Test void onePackOfStackCanBeQuotedAndTheOtherRemainsOwned() {
        var one=EchoResourceInstance.pack(match,UUID.randomUUID(),EchoDraftService.PackQuality.COMMON,UUID.randomUUID());
        var two=EchoResourceInstance.pack(match,UUID.randomUUID(),EchoDraftService.PackQuality.COMMON,UUID.randomUUID());
        for(var pack:List.of(one,two)) { var from=Location.ground(UUID.randomUUID()); ledger.register(match,pack.instanceId(),from); a.receive(pack,from,a.revision()); }
        var t=editing(); quote(t,a,one.instanceId());
        assertTrue(a.locked(one.instanceId())); assertFalse(a.locked(two.instanceId()));
        t.confirm(a.owner(),t.version(),a.revision(),0); assertTrue(t.confirm(b.owner(),t.version(),b.revision(),0));
        assertTrue(a.find(two.instanceId()).isPresent()); assertTrue(b.find(one.instanceId()).isPresent());
    }
    @Test void expirationAndExactCancelReleaseOnlyTheirOwnToken() {
        var ia=give(a,"short_dash"); var t=editing(); quote(t,a,ia.instanceId());
        assertFalse(t.cancel(UUID.randomUUID())); assertTrue(a.locked(ia.instanceId()));
        assertFalse(t.expire(399)); assertTrue(t.expire(400)); assertFalse(a.locked(ia.instanceId()));
        var next=editing(); quote(next,a,ia.instanceId()); assertFalse(t.cancel(t.id())); assertTrue(a.locked(ia.instanceId()));
        next.cancel(next.id()); assertFalse(a.locked(ia.instanceId()));
    }
    @Test void keyQuotaAndReservationsCheckedAgainstFinalLayout() {
        var ia=give(a,"sound_key"); give(b,"master_key"); var t=editing(); quote(t,a,ia.instanceId());
        assertEquals("KEY_LIMIT",t.layoutError()); t.cancel(t.id());
        var key=b.key().orElseThrow(); b.remove(key.instanceId(),Location.consumed(),b.revision(),null); b.reserveKey(true);
        var reserved=editing(); quote(reserved,a,ia.instanceId()); assertEquals("KEY_LIMIT",reserved.layoutError());
    }
    @Test void frozenRuleValuesAndOutsiderCannotMutate() {
        var t=new EchoTradeState(a,b,1200,16); assertEquals(1200,t.deadline()); assertEquals(16,t.range());
        assertThrows(IllegalStateException.class,() -> t.accept(UUID.randomUUID(),t.version(),0)); assertEquals(EchoTradeState.Status.PROPOSED,t.status());
    }
}
