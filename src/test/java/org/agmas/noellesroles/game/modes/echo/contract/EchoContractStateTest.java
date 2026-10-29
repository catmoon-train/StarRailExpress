package org.agmas.noellesroles.game.modes.echo.contract;

import org.agmas.noellesroles.game.modes.echo.state.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoContractStateTest {
    @Test void smugglingCountsDistinctSuccessfulDeliveriesAndLocksAcrossDeath() {
        var s=new EchoContractState(EchoContract.SMUGGLER); UUID a=UUID.randomUUID();
        s.delivered(a,10); s.delivered(a,20); assertFalse(s.succeeded());
        s.delivered(UUID.randomUUID(),30); assertTrue(s.succeeded()); assertTrue(s.mayRetire());
        s.depart(); s.finish(true,false,EchoLifeState.ELIMINATED,40); assertTrue(s.succeeded()); assertEquals(30,s.snapshot("west",false).succeededAt());
    }
    @Test void scavengerRequiresThreeResolvedDistinctPacksAndNormalOnlineStandingFinal() {
        for(var life:EchoLifeState.values()) for(boolean online:List.of(true,false)) {
            var s=new EchoContractState(EchoContract.SCAVENGER); UUID pack=UUID.randomUUID();
            s.resolved(pack); s.resolved(pack); s.resolved(UUID.randomUUID()); assertFalse(s.armed());
            s.resolved(UUID.randomUUID()); assertTrue(s.armed()); assertFalse(s.mayRetire());
            s.finish(true,online,life,100); assertEquals(online&&life.standing(),s.succeeded(),life+" online="+online);
        }
    }
    @Test void departureCannotBeUndoneByLatePackOrKeyFacts() {
        var scavenger=new EchoContractState(EchoContract.SCAVENGER); scavenger.depart();
        for(int n=0;n<4;n++) scavenger.resolved(UUID.randomUUID()); scavenger.finish(true,true,EchoLifeState.HEALTHY,100);
        assertEquals(EchoContractState.Status.FAILED,scavenger.status());
        var dealer=new EchoContractState(EchoContract.KEY_DEALER); dealer.depart(); dealer.keyConsumed(100); assertFalse(dealer.succeeded());
    }
    @Test void voidNeverGrantsPersonalWinEvenAfterInstantSuccess() {
        var s=new EchoContractState(EchoContract.KEY_DEALER); s.keyConsumed(10); assertTrue(s.succeeded());
        s.finish(false,true,EchoLifeState.HEALTHY,20); assertEquals(EchoContractState.Status.VOID,s.status()); assertFalse(s.succeeded());
    }
    @Test void informationRequiresTwoTrueFactionsAndBidirectionalNonemptyEvents() {
        var s=new EchoContractState(EchoContract.INFORMATION_DEALER); UUID peer=UUID.randomUUID(),event=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        s.traded(event,peer,EchoFaction.CARRIER,List.of(a),List.of(),10); assertEquals(0,s.snapshot("",false).primary());
        s.traded(event,peer,EchoFaction.BROKER,List.of(a),List.of(b),10); assertEquals(0,s.snapshot("",false).primary());
        s.traded(event,peer,EchoFaction.CARRIER,List.of(a),List.of(b),10); assertEquals(1,s.snapshot("",false).primary());
        s.traded(event,UUID.randomUUID(),EchoFaction.INTERCEPTOR,List.of(a),List.of(b),20); assertFalse(s.succeeded());
        s.traded(UUID.randomUUID(),peer,EchoFaction.INTERCEPTOR,List.of(b),List.of(a),20); assertFalse(s.succeeded());
        s.traded(UUID.randomUUID(),UUID.randomUUID(),EchoFaction.INTERCEPTOR,List.of(UUID.randomUUID()),List.of(UUID.randomUUID()),30); assertTrue(s.succeeded());
        assertEquals(1,s.snapshot("",false).secondary());
    }
    @Test void keyChainRequiresExactInstanceRecipientAndOnlyLatestChain() {
        var keys=new EchoKeyProvenance(); UUID a=UUID.randomUUID(),b=UUID.randomUUID(),dealer=UUID.randomUUID(),carrier=UUID.randomUUID(),trade=UUID.randomUUID();
        keys.traded(a,trade,dealer,carrier); assertTrue(keys.consume(a,UUID.randomUUID()).isEmpty());
        keys.traded(a,trade,dealer,carrier); keys.traded(b,UUID.randomUUID(),dealer,carrier); assertTrue(keys.consume(a,carrier).isEmpty());
        var chain=keys.consume(b,carrier).orElseThrow(); assertEquals(dealer,chain.dealer()); assertTrue(keys.consume(b,carrier).isEmpty());
    }
    @Test void dropTransferCraftOrTheftInvalidatesUntilAnewRealTrade() {
        var keys=new EchoKeyProvenance(); UUID a=UUID.randomUUID(),dealer=UUID.randomUUID(),carrier=UUID.randomUUID();
        keys.traded(a,UUID.randomUUID(),dealer,carrier); keys.invalidate(a); assertTrue(keys.consume(a,carrier).isEmpty());
        keys.traded(a,UUID.randomUUID(),dealer,carrier); assertTrue(keys.consume(a,carrier).isPresent());
        keys.traded(a,UUID.randomUUID(),dealer,carrier); keys.clear(); assertTrue(keys.all().isEmpty());
    }
    @Test void blackNodesStartWithOneAndRecoverAfterSixtySeconds() {
        var black=new EchoBlackCrystalState(List.of("west","east")); UUID a=UUID.randomUUID(),owner=UUID.randomUUID();
        assertTrue(black.available("west",0)); black.collect(owner,"west",a,0); assertFalse(black.available("west",1199));
        assertTrue(black.available("east",0)); assertTrue(black.available("west",1200)); assertTrue(black.owned(a,owner)); assertFalse(black.owned(a,UUID.randomUUID()));
        assertThrows(IllegalStateException.class,() -> black.collect(owner,"east",a,0));
    }
    @Test void blackReturnReopensOriginalNodeAndConsumptionDoesNotRefreshIt() {
        var black=new EchoBlackCrystalState(List.of("west")); UUID owner=UUID.randomUUID(),a=UUID.randomUUID();
        black.collect(owner,"west",a,0); black.recycle(a,600); assertTrue(black.available("west",600)); assertFalse(black.black(a));
        UUID b=UUID.randomUUID(); black.collect(owner,"west",b,600); black.consume(b); assertFalse(black.available("west",1799)); assertTrue(black.available("west",1800));
    }
}
