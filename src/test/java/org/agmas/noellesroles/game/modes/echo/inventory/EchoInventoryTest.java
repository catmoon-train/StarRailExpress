package org.agmas.noellesroles.game.modes.echo.inventory;

import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceLedger.Location;
import static org.junit.jupiter.api.Assertions.*;

class EchoInventoryTest {
    private final UUID match=UUID.randomUUID(),owner=UUID.randomUUID();
    private final EchoResourceLedger ledger=new EchoResourceLedger(match);
    private final EchoInventory bag=new EchoInventory(owner,ledger);
    private EchoResourceInstance card(String id) { return EchoResourceInstance.card(match,UUID.randomUUID(),EchoCardRegistry.require("noellesroles:echo/"+id),UUID.randomUUID()); }
    private EchoResourceInstance pack(EchoDraftService.PackQuality quality) { return EchoResourceInstance.pack(match,UUID.randomUUID(),quality,UUID.randomUUID()); }
    private void give(EchoInventory bag,EchoResourceInstance item) {
        Location from=Location.ground(UUID.randomUUID()); ledger.register(match,item.instanceId(),from); bag.receive(item,from,bag.revision());
    }
    @Test void sameQualityPacksStackByTwoAndSnapshotsCannotMutate() {
        var a=pack(EchoDraftService.PackQuality.COMMON); var b=pack(EchoDraftService.PackQuality.COMMON); var c=pack(EchoDraftService.PackQuality.COMMON);
        give(bag,a); give(bag,b); give(bag,c); give(bag,pack(EchoDraftService.PackQuality.RARE));
        assertEquals(0,bag.slot(a.instanceId())); assertEquals(0,bag.slot(b.instanceId())); assertEquals(1,bag.slot(c.instanceId()));
        var snapshot=bag.snapshot(); assertEquals(6,snapshot.slots().size());
        assertThrows(UnsupportedOperationException.class,() -> snapshot.slots().clear());
        assertThrows(UnsupportedOperationException.class,() -> snapshot.slots().getFirst().items().clear());
        assertThrows(UnsupportedOperationException.class,() -> snapshot.locks().clear());
    }
    @Test void fullBagIncludingSinglePackHasNoExtraReservationSlot() {
        give(bag,pack(EchoDraftService.PackQuality.COMMON)); for(int n=0;n<5;n++) give(bag,card("short_dash"));
        var old=bag.snapshot(); assertThrows(IllegalStateException.class,() -> bag.reserve(UUID.randomUUID(),bag.revision()));
        assertEquals(old,bag.snapshot()); assertEquals(6,bag.items().size());
    }
    @Test void reservedSlotCannotReceiveOrMoveAndKeyReservationBlocksAllKeys() {
        UUID token=UUID.randomUUID(); int reserved=bag.reserve(token,bag.revision());
        var dash=card("short_dash"); give(bag,dash); assertNotEquals(reserved,bag.slot(dash.instanceId()));
        assertThrows(IllegalStateException.class,() -> bag.move(dash.instanceId(),reserved,bag.revision()));
        bag.reserveKey(true); assertFalse(bag.canReceive(card("sound_key"))); assertFalse(bag.canReceive(card("master_key")));
        var chosen=card("master_key"); bag.resolve(token,chosen);
        assertEquals(reserved,bag.slot(chosen.instanceId())); assertFalse(bag.snapshot().keyReserved()); assertFalse(bag.keyAvailable());
        assertThrows(IllegalStateException.class,() -> bag.resolve(token,card("short_dash")));
    }
    @Test void cancelledReservationDoesNotUnlockBoundPackOrReleaseKeyQuota() {
        var pack=pack(EchoDraftService.PackQuality.RARE); give(bag,pack); UUID token=UUID.randomUUID();
        bag.lock(pack.instanceId(),token,bag.revision()); bag.reserve(token,bag.revision()); bag.reserveKey(true); bag.release(token);
        assertTrue(bag.locked(pack.instanceId())); assertFalse(bag.keyAvailable());
        assertThrows(IllegalStateException.class,() -> bag.remove(pack.instanceId(),Location.consumed(),bag.revision(),null));
        bag.unlock(UUID.randomUUID()); assertTrue(bag.locked(pack.instanceId()));
    }
    @Test void sameDefinitionDifferentInstancesCraftInFullBagWithoutThirdSlot() {
        var a=card("short_dash"); var b=card("short_dash"); give(bag,a); give(bag,b);
        for(int n=0;n<4;n++) give(bag,card("steady_flow")); assertEquals(-1,bag.emptySlot());
        var output=bag.craft(a.instanceId(),b.instanceId(),EchoFaction.CARRIER,bag.revision());
        assertEquals("noellesroles:echo/long_dash",output.definitionId()); assertEquals(0,bag.slot(output.instanceId()));
        assertEquals(5,bag.items().size()); assertTrue(ledger.find(a.instanceId()).orElseThrow().terminal());
        assertTrue(ledger.find(b.instanceId()).orElseThrow().terminal());
    }
    @Test void invalidCraftAndStaleRevisionAreZeroMutation() {
        var a=card("short_dash"); var b=card("steady_flow"); give(bag,a); give(bag,b);
        var snapshot=bag.snapshot(); var locations=ledger.snapshot();
        assertThrows(IllegalStateException.class,() -> bag.craft(a.instanceId(),b.instanceId(),EchoFaction.CARRIER,bag.revision()));
        assertThrows(IllegalStateException.class,() -> bag.craft(a.instanceId(),a.instanceId(),EchoFaction.CARRIER,bag.revision()));
        assertThrows(IllegalStateException.class,() -> bag.move(a.instanceId(),3,0));
        assertEquals(snapshot,bag.snapshot()); assertEquals(locations,ledger.snapshot());
    }
    @Test void fullBagsCanAtomicallyExchangeOneForOneIncludingPackAndKey() {
        var other=new EchoInventory(UUID.randomUUID(),ledger); var a=card("sound_key"); var b=pack(EchoDraftService.PackQuality.COMMON);
        give(bag,a); give(other,b);
        for(int n=0;n<5;n++) { give(bag,card("short_dash")); give(other,card("steady_flow")); }
        UUID trade=UUID.randomUUID(); bag.lock(a.instanceId(),trade,bag.revision()); other.lock(b.instanceId(),trade,other.revision());
        EchoInventory.exchange(bag,List.of(a.instanceId()),bag.revision(),other,List.of(b.instanceId()),other.revision(),trade);
        assertTrue(bag.find(b.instanceId()).isPresent()); assertTrue(other.find(a.instanceId()).isPresent());
        assertEquals(6,bag.items().size()); assertEquals(6,other.items().size()); assertFalse(bag.locked(a.instanceId()));
        assertEquals(Location.inventory(other.owner(),other.slot(a.instanceId())),ledger.find(a.instanceId()).orElseThrow());
    }
    @Test void failedExchangeDoesNotMoveFirstSideAndDoesNotUnlock() {
        var other=new EchoInventory(UUID.randomUUID(),ledger); var keyA=card("sound_key"); var keyB=card("master_key");
        give(bag,keyA); give(other,keyB); UUID trade=UUID.randomUUID(); bag.lock(keyA.instanceId(),trade,bag.revision());
        var beforeA=bag.snapshot(); var beforeB=other.snapshot(); var beforeLedger=ledger.snapshot();
        assertThrows(IllegalStateException.class,() -> EchoInventory.exchange(bag,List.of(keyA.instanceId()),bag.revision(),other,List.of(),other.revision(),trade));
        assertEquals(beforeA,bag.snapshot()); assertEquals(beforeB,other.snapshot()); assertEquals(beforeLedger,ledger.snapshot());
    }
    @Test void oneWayTransferOnlyMovesUnlockedInstanceOfStack() {
        var receiver=new EchoInventory(UUID.randomUUID(),ledger);
        var bound=pack(EchoDraftService.PackQuality.COMMON); var free=pack(EchoDraftService.PackQuality.COMMON);
        give(bag,bound); give(bag,free); bag.lock(bound.instanceId(),UUID.randomUUID(),bag.revision());
        assertThrows(IllegalStateException.class,() -> EchoInventory.exchange(bag,List.of(bound.instanceId()),bag.revision(),receiver,List.of(),receiver.revision(),null));
        EchoInventory.exchange(bag,List.of(free.instanceId()),bag.revision(),receiver,List.of(),receiver.revision(),null);
        assertTrue(bag.find(bound.instanceId()).isPresent()); assertTrue(bag.locked(bound.instanceId()));
        assertTrue(bag.find(free.instanceId()).isEmpty()); assertTrue(receiver.find(free.instanceId()).isPresent());
        assertEquals(Location.inventory(receiver.owner(),0),ledger.find(free.instanceId()).orElseThrow());
    }
    @Test void fullReceiverRejectsOneWayTransferWithZeroMutation() {
        var receiver=new EchoInventory(UUID.randomUUID(),ledger); var pack=pack(EchoDraftService.PackQuality.COMMON); give(bag,pack);
        for(int i=0;i<6;i++) give(receiver,card("short_dash"));
        var before=ledger.snapshot(); var a=bag.snapshot(); var b=receiver.snapshot();
        assertThrows(IllegalStateException.class,() -> EchoInventory.exchange(bag,List.of(pack.instanceId()),bag.revision(),receiver,List.of(),receiver.revision(),null));
        assertEquals(before,ledger.snapshot()); assertEquals(a,bag.snapshot()); assertEquals(b,receiver.snapshot());
    }
    @Test void creationFailureDoesNotConsumeInputsOrLeaveNewResource() {
        var a=card("short_dash"); give(bag,a); var before=ledger.snapshot();
        assertThrows(IllegalStateException.class,() -> ledger.commit(match,List.of(new EchoResourceLedger.Move(a.instanceId(),Location.inventory(owner,0),Location.consumed())),Map.of(a.instanceId(),Location.ground(UUID.randomUUID()))));
        assertEquals(before,ledger.snapshot());
    }
    @Test void keySynthesisChecksFinalCapacityIncludingReservedQuota() {
        var a=card("door_wedge"); var b=card("door_wedge"); give(bag,a); give(bag,b); bag.reserveKey(true);
        var before=bag.snapshot(); assertThrows(IllegalStateException.class,() -> bag.craft(a.instanceId(),b.instanceId(),EchoFaction.BROKER,bag.revision()));
        assertEquals(before,bag.snapshot()); bag.reserveKey(false);
        assertTrue(bag.craft(a.instanceId(),b.instanceId(),EchoFaction.BROKER,bag.revision()).key());
    }
    @Test void duplicateReservationAndUnreservedKeyOutputAreZeroMutation() {
        UUID token=UUID.randomUUID(); bag.reserve(token,bag.revision()); var before=bag.snapshot(); var locations=ledger.snapshot();
        assertThrows(IllegalStateException.class,() -> bag.reserve(token,bag.revision()));
        assertThrows(IllegalStateException.class,() -> bag.resolve(token,card("sound_key")));
        assertEquals(before,bag.snapshot()); assertEquals(locations,ledger.snapshot());
    }
}
