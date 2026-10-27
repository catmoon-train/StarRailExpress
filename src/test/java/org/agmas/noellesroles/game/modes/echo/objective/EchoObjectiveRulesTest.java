package org.agmas.noellesroles.game.modes.echo.objective;

import org.agmas.noellesroles.game.modes.echo.action.EchoActionService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoObjectiveRulesTest {
    @Test void chargeHasExactDurationAndSupplyCap() {
        for(int escorts=0;escorts<=3;escorts++) {
            EchoBeaconState beacon=new EchoBeaconState();
            assertTrue(beacon.submit()); assertTrue(beacon.submit()); assertTrue(beacon.submit()); assertFalse(beacon.submit());
            int expected=(int)Math.ceil((double)org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.beacon().chargeTicks()/(1+0.2*escorts));
            for(int tick=0;tick<expected-1;tick++) assertFalse(beacon.tick(tick,escorts));
            assertTrue(beacon.tick(expected-1,escorts)); assertTrue(beacon.active()); assertEquals(1000,beacon.progressPermille());
            assertFalse(beacon.stall(expected,20));
        }
    }
    @Test void stallPausesChargingWithoutDeductingProgress() {
        EchoBeaconState beacon=new EchoBeaconState(); for(int n=0;n<3;n++) beacon.submit();
        int half=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.beacon().chargeTicks()/2;
        for(int n=0;n<half;n++) beacon.tick(n,0);
        assertEquals(500,beacon.progressPermille());
        assertTrue(beacon.stall(half,400)); assertEquals(500,beacon.progressPermille(),"迟滞不扣任何已积累进度");
        assertFalse(beacon.canStall(half),"迟滞中不能重复施加");
        for(int tick=half;tick<half+400;tick++) assertFalse(beacon.tick(tick,3));
        assertEquals(500,beacon.progressPermille());
        assertTrue(beacon.canStall(half+400));
        beacon.tick(half+400,0); beacon.tick(half+401,0); assertTrue(beacon.progressPermille()>500,"迟滞结束后恢复增长");
    }
    @Test void submitStopsAtRequiredCrystalsAndNeverAfterActive() {
        EchoBeaconState beacon=new EchoBeaconState();
        for(int n=0;n<3;n++) assertTrue(beacon.submit(),"第三枚应当成功");
        assertEquals(3,beacon.delivered());
        assertFalse(beacon.canSubmit(),"满料后不能再投");
        assertFalse(beacon.submit(),"满料后提交必须返回 false");
        assertEquals(3,beacon.delivered(),"多余的提交不能增加计数");
        int full=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.beacon().chargeTicks();
        for(int tick=0;tick<full;tick++) beacon.tick(tick,0);
        assertTrue(beacon.active());
        assertFalse(beacon.canSubmit(),"已启动的信标不能再投料");
        assertFalse(beacon.submit());
        assertEquals(3,beacon.delivered());
    }
    @Test void escortBonusOnlyAppliesWhenEscortsAreReported() {
        int full=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.beacon().chargeTicks();
        var alone=new EchoBeaconState(); for(int n=0;n<3;n++) alone.submit();
        var escorted=new EchoBeaconState(); for(int n=0;n<3;n++) escorted.submit();
        // 同样 tick 数下，无人护送不应超过有人护送
        for(int tick=0;tick<full/4;tick++) { alone.tick(tick,0); escorted.tick(tick,3); }
        assertTrue(escorted.progressPermille()>alone.progressPermille(),
                "三人护送必须比无人护送更快");
        // 无人护送时进度只按基础速率增长，不会被护送加成
        var fresh=new EchoBeaconState(); for(int n=0;n<3;n++) fresh.submit();
        int quarter=full/4;
        for(int tick=0;tick<quarter;tick++) fresh.tick(tick,0);
        assertEquals(250,fresh.progressPermille(),"无人护送时 1/4 时间只能是 25%");
    }

    @Test void boostAdvancesChargingButNeverStartsUnfedBeacon() {
        EchoBeaconState beacon=new EchoBeaconState(); assertFalse(beacon.boost(0,500),"未满料不能加速");
        for(int n=0;n<3;n++) beacon.submit();
        assertTrue(beacon.boost(0,500)); assertEquals(500,beacon.progressPermille());
        assertTrue(beacon.boost(1,500)); assertTrue(beacon.active());
        assertFalse(beacon.boost(2,100),"已启动不能再加速");
        assertFalse(beacon.stall(2,400),"已启动不能被迟滞");
    }
    @Test void overloadOnceKeepsCoreOpenAndReopensAfterExactlyThirtySeconds() {
        EchoGateState gates=new EchoGateState();
        assertFalse(gates.overload(0)); assertTrue(gates.openCore(0,300)); assertFalse(gates.openCore(1,300));
        assertTrue(gates.extendCore(1,100,500)); assertTrue(gates.coreOpen(399)); assertFalse(gates.coreOpen(400));
        gates.beginEvacuation(); assertTrue(gates.overload(500)); assertTrue(gates.coreOpen(501));
        assertFalse(gates.evacuationOpen(1099)); assertTrue(gates.evacuationOpen(1100)); assertFalse(gates.overload(1100));
    }
    @Test void keyReplenishmentDoesNotDependOnKeysHeldByPlayers() {
        EchoKeySchedule keys=new EchoKeySchedule(List.of("a","b","c"),0);
        var initial=keys.initial(new Random(1)); assertEquals(2,new HashSet<>(initial).size());
        UUID held=UUID.randomUUID(); assertTrue(keys.occupy(initial.getFirst(),held));
        keys.release(initial.getFirst(),UUID.randomUUID()); assertTrue(keys.at(initial.getFirst()).isPresent());
        keys.release(initial.getFirst(),held);
        assertTrue(keys.due(1199).isEmpty()); assertTrue(keys.due(1200).isPresent()); assertTrue(keys.due(1200).isEmpty());
        for(String altar:List.of("a","b","c")) assertTrue(keys.occupy(altar,UUID.randomUUID()));
        assertTrue(keys.due(2400).isEmpty());
    }
    @Test void leasesAreExclusiveCancelByIdAndDoNotCompleteAfterExpiry() {
        EchoActionService actions=new EchoActionService(); UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var first=actions.request(a,"node",80,0,0,0,true).orElseThrow();
        assertTrue(actions.request(b,"node",80,0,0,0,true).isEmpty());
        for(int tick=5;tick<=80;tick+=5) {
            assertEquals(first.id(),actions.request(a,"node",80,tick,0,0,true).orElseThrow().id());
            var done=actions.advance(tick,action -> true);
            assertEquals(tick==80?1:0,done.size());
        }
        var newer=actions.request(a,"node",80,81,0,0,true).orElseThrow();
        assertFalse(actions.cancel(a,first.id())); assertTrue(actions.cancel(a,newer.id()));
        actions.request(b,"node",80,82,0,0,true);
        assertTrue(actions.advance(93,action -> true).isEmpty()); assertTrue(actions.current(b).isEmpty());
    }
    @Test void twoNonexclusiveSubmissionsKeepOrderAndOnlyLastSlotCommits() {
        EchoActionService actions=new EchoActionService(); UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        EchoBeaconState beacon=new EchoBeaconState(); beacon.submit(); beacon.submit();
        actions.request(a,"beacon",1,0,0,0,false); actions.request(b,"beacon",1,0,0,0,false);
        var ready=actions.advance(1,action -> true); assertEquals(a,ready.getFirst().player());
        assertTrue(beacon.submit()); assertFalse(beacon.submit()); assertEquals(3,beacon.delivered());
    }
    @Test void renewedThenCancelledLeaseCannotCompleteOnSameTick() {
        EchoActionService actions=new EchoActionService(); UUID player=UUID.randomUUID();
        var action=actions.request(player,"crystal",80,0,0,0,true).orElseThrow();
        actions.request(player,"crystal",80,80,0,0,true);
        assertTrue(actions.cancel(player,action.id()));
        assertTrue(actions.advance(80,a -> true).isEmpty());
    }
    @Test void lastTickBeaconCompletionStartsFullEvacuationAndOverloadDoesNotResetClock() {
        var clock=new org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock();
        clock.capture(0,1,false); clock.finish(0,false);
        EchoBeaconState beacon=new EchoBeaconState(); for(int n=0;n<3;n++) beacon.submit();
        int full=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.beacon().chargeTicks();
        for(int tick=0;tick<full-1;tick++) beacon.tick(tick,0);
        clock.capture(1,1,false); assertTrue(beacon.tick(full-1,0));
        assertTrue(clock.openEvacuation(false));
        assertEquals(org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock.Boundary.NONE,clock.finish(2400,false));
        EchoGateState gate=new EchoGateState(); gate.beginEvacuation();
        clock.capture(2,2400,false); assertTrue(gate.overload(clock.matchTick()));
        assertEquals(org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock.Boundary.NONE,clock.finish(2399,false));
        assertFalse(gate.evacuationOpen(clock.matchTick()));
    }
}
