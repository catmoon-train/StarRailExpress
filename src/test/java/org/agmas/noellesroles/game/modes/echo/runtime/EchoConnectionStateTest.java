package org.agmas.noellesroles.game.modes.echo.runtime;

import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.combat.EchoCombatState;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.state.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger.Location;
import static org.junit.jupiter.api.Assertions.*;

class EchoConnectionStateTest {
    @Test void onlyOneGraceAndDuplicateDisconnectDoesNotRefreshDeadline() {
        UUID match=UUID.randomUUID(); var c=new EchoConnectionState(match);
        assertTrue(c.disconnect(100)); assertEquals(700,c.deadline());
        assertFalse(c.disconnect(200)); assertEquals(700,c.deadline());
        assertTrue(c.reconnect(match,699)); assertFalse(c.disconnect(700));
        assertEquals(EchoConnectionState.Status.EXPIRED,c.status()); assertFalse(c.reconnect(match,701));
    }
    @Test void exactDeadlineAndForeignMatchCannotReconnect() {
        UUID match=UUID.randomUUID(); var c=new EchoConnectionState(match); c.disconnect(50);
        assertFalse(c.reconnect(UUID.randomUUID(),51)); assertTrue(c.inGrace(649)); assertFalse(c.inGrace(650));
        assertFalse(c.reconnect(match,650)); assertTrue(c.expire(650)); assertFalse(c.expire(651));
    }
    @Test void pauseDoesNotAdvanceGraceAndActiveLeaveNeverGetsGrace() {
        UUID match=UUID.randomUUID(); var c=new EchoConnectionState(match); c.disconnect(50);
        for(int i=0;i<1000;i++) { assertTrue(c.inGrace(50)); assertFalse(c.expire(50)); }
        c.leave(); assertFalse(c.reconnect(match,51)); assertFalse(c.disconnect(51));
    }
    @Test void freshConnectionWindowRejectsOldEpochAndRestartsRequestSequence() {
        var old=new EchoRequestWindow(); assertEquals("",old.accept(1,old.epoch(),100,0,false)); old.remember(100,"");
        var next=new EchoRequestWindow(); assertNotEquals(old.epoch(),next.epoch());
        assertEquals("INVALID_SESSION",next.accept(1,old.epoch(),101,500,false));
        assertEquals("",next.accept(1,next.epoch(),1,500,false));
    }
    @Test void bleedDeadlineIsNotExtendedByDisconnectOrReconnect() {
        UUID match=UUID.randomUUID(); var c=new EchoConnectionState(match); var combat=new EchoCombatState();
        assertEquals(EchoCombatState.Hit.DAMAGED,combat.hit(10,false,false));
        assertEquals(EchoCombatState.Hit.DOWNED,combat.hit(10,false,false)); assertEquals(1360,combat.bleedUntil());
        c.disconnect(600); assertTrue(c.reconnect(match,900)); assertEquals(1360,combat.bleedUntil());
        assertFalse(combat.expireBleed(1359)); assertTrue(combat.expireBleed(1360)); assertEquals(EchoLifeState.ELIMINATED,combat.life());
    }
    @Test void detachedProjectionCannotChangeCanonicalCargoSnapshot() {
        var cargo=new EchoCargoState(); UUID first=UUID.randomUUID(),second=UUID.randomUUID(); cargo.add(first,2);
        var oldProjection=cargo.items(); cargo.add(second,2);
        assertEquals(List.of(first),oldProjection); assertThrows(UnsupportedOperationException.class,oldProjection::clear);
        cargo.remove(first); assertEquals(List.of(second),cargo.items());
        assertThrows(IllegalStateException.class,() -> cargo.add(second,2));
    }
    @Test void offeredPackResolvesOfflineOnceIntoSameReservedInventory() {
        UUID match=UUID.randomUUID(),owner=UUID.randomUUID(),token=UUID.randomUUID();
        var ledger=new EchoResourceLedger(match); var bag=new EchoInventory(owner,ledger);
        var pack=EchoResourceInstance.pack(match,UUID.randomUUID(),EchoDraftService.PackQuality.COMMON,UUID.randomUUID());
        var source=Location.ground(UUID.randomUUID()); ledger.register(match,pack.instanceId(),source); bag.receive(pack,source,bag.revision());
        bag.lock(pack.instanceId(),token,bag.revision()); bag.reserve(token,bag.revision());
        var draft=new EchoPackDraft(pack.instanceId(),pack.quality());
        assertTrue(draft.begin(owner,0,true,EchoDraftService.Context.all(EchoFaction.CARRIER,false,false),new Random(4)));
        assertTrue(draft.advance(30).isEmpty()); assertEquals(430,draft.choiceEnd());
        bag.remove(pack.instanceId(),Location.consumed(),bag.revision(),token);
        var c=new EchoConnectionState(match); c.disconnect(40);
        var resolution=draft.advance(430).orElseThrow(); assertTrue(resolution.automatic());
        var output=EchoResourceInstance.card(match,UUID.randomUUID(),resolution.chosen(),pack.originEventId()); bag.resolve(token,output); bag.unlock(token);
        assertTrue(draft.advance(431).isEmpty()); assertTrue(c.reconnect(match,500));
        assertEquals(List.of(output),bag.items()); assertEquals(Location.consumed(),ledger.find(pack.instanceId()).orElseThrow());
        assertEquals(Location.inventory(owner,bag.slot(output.instanceId())),ledger.find(output.instanceId()).orElseThrow());
    }
    @Test void interruptedChannelKeepsBindingAndDoesNotOfferWhileOffline() {
        UUID owner=UUID.randomUUID(); var draft=new EchoPackDraft(UUID.randomUUID(),EchoDraftService.PackQuality.COMMON);
        assertTrue(draft.begin(owner,0,true,EchoDraftService.Context.all(EchoFaction.CARRIER,false,false),new Random(5)));
        assertTrue(draft.cancel(owner,10)); assertEquals(210,draft.readyAt()); assertTrue(draft.advance(500).isEmpty());
        assertEquals(EchoPackDraft.State.BOUND,draft.state()); assertTrue(draft.visibleCandidates(owner).isEmpty());
        assertFalse(draft.begin(UUID.randomUUID(),500,true,EchoDraftService.Context.all(EchoFaction.CARRIER,false,false),new Random(6)));
        assertTrue(draft.begin(owner,500,true,EchoDraftService.Context.all(EchoFaction.CARRIER,false,false),new Random(6)));
        draft.advance(530); assertEquals(930,draft.choiceEnd());
    }
}
