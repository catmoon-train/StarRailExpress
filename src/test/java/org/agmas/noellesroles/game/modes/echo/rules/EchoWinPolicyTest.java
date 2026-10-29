package org.agmas.noellesroles.game.modes.echo.rules;

import org.agmas.noellesroles.game.modes.echo.state.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EchoWinPolicyTest {
    private static EchoWinPolicy.PlayerFact fact(EchoFaction faction, EchoLifeState life) {
        return new EchoWinPolicy.PlayerFact(faction, life, true, false);
    }
    private static EchoWinPolicy.Decision check(EchoPhase phase, int time, boolean aborted,
                                               List<EchoWinPolicy.PlayerFact> facts) {
        return EchoWinPolicy.evaluate(new EchoWinPolicy.Facts(phase, time, 3, aborted, facts));
    }

    @Test void oneRemainingCarrierNeverLosesToALegacyQuota() {
        var facts=List.of(fact(EchoFaction.CARRIER,EchoLifeState.HEALTHY),fact(EchoFaction.INTERCEPTOR,EchoLifeState.HEALTHY));
        for(int quota:new int[]{0,3,14,32}) for(var phase:List.of(EchoPhase.OPERATIONS,EchoPhase.EVACUATION))
            assertEquals(EchoWinPolicy.Outcome.NONE,EchoWinPolicy.evaluate(new EchoWinPolicy.Facts(phase,20,quota,false,facts)).outcome());
    }

    @Test void firstEscapeLocksVictoryWithoutStoppingOtherPlayers() {
        var facts=new ArrayList<>(List.of(fact(EchoFaction.CARRIER,EchoLifeState.ESCAPED),
                fact(EchoFaction.CARRIER,EchoLifeState.DOWNED),fact(EchoFaction.INTERCEPTOR,EchoLifeState.HEALTHY)));
        assertEquals(EchoWinPolicy.Outcome.NONE,check(EchoPhase.EVACUATION,20,false,facts).outcome());
        facts.set(2,new EchoWinPolicy.PlayerFact(EchoFaction.INTERCEPTOR,EchoLifeState.HEALTHY,false,false));
        assertEquals(EchoWinPolicy.Outcome.NONE,check(EchoPhase.EVACUATION,20,false,facts).outcome());
        assertEquals(EchoWinPolicy.Outcome.CARRIERS,check(EchoPhase.EVACUATION,0,false,facts).outcome());
        facts.set(1,fact(EchoFaction.CARRIER,EchoLifeState.ELIMINATED));
        assertEquals(EchoWinPolicy.Reason.ESCAPE_COMPLETED,check(EchoPhase.EVACUATION,20,false,facts).reason());
    }

    @Test void noEscapeOnlyLosesWhenAllChancesAreGoneOrTimeExpires() {
        var facts=new ArrayList<>(List.of(fact(EchoFaction.CARRIER,EchoLifeState.DOWNED),fact(EchoFaction.INTERCEPTOR,EchoLifeState.HEALTHY)));
        assertEquals(EchoWinPolicy.Outcome.NONE,check(EchoPhase.EVACUATION,20,false,facts).outcome());
        assertEquals(EchoWinPolicy.Reason.TIME_EXPIRED,check(EchoPhase.EVACUATION,0,false,facts).reason());
        facts.set(0,fact(EchoFaction.CARRIER,EchoLifeState.ELIMINATED));
        assertEquals(EchoWinPolicy.Reason.NO_CARRIERS_LEFT,check(EchoPhase.EVACUATION,20,false,facts).reason());
    }

    @Test void publicSnapshotSupportsUnlimitedQuotaSentinel() {
        var state=new EchoPublicState(EchoPhase.EVACUATION,100,1200,false,21,0,true,0,true,0,false,List.of());
        assertEquals(0,state.quota()); assertEquals(21,state.escaped());
        assertThrows(IllegalArgumentException.class,() -> new EchoWinPolicy.Facts(EchoPhase.EVACUATION,20,-1,false,List.of()));
    }

    @Test void lastTickEscapeWinsBeforeTimeoutButAbortWinsEverything() {
        var facts = List.of(fact(EchoFaction.CARRIER, EchoLifeState.ESCAPED),
                fact(EchoFaction.CARRIER, EchoLifeState.ESCAPED), fact(EchoFaction.CARRIER, EchoLifeState.ESCAPED));
        assertEquals(EchoWinPolicy.Outcome.CARRIERS, check(EchoPhase.EVACUATION,0,false,facts).outcome());
        assertEquals(EchoWinPolicy.Outcome.VOID, check(EchoPhase.EVACUATION,0,true,facts).outcome());
        assertEquals(EchoWinPolicy.Outcome.NONE, check(EchoPhase.SAFE,0,false,facts).outcome());
    }

    @Test void downedAndGraceArePotentialButExpiredDisconnectIsNot() {
        List<EchoWinPolicy.PlayerFact> facts = new ArrayList<>(List.of(
                fact(EchoFaction.CARRIER, EchoLifeState.HEALTHY), fact(EchoFaction.CARRIER, EchoLifeState.DOWNED),
                new EchoWinPolicy.PlayerFact(EchoFaction.CARRIER, EchoLifeState.WOUNDED,false,true),
                fact(EchoFaction.INTERCEPTOR,EchoLifeState.HEALTHY)));
        assertEquals(EchoWinPolicy.Outcome.NONE, check(EchoPhase.OPERATIONS,20,false,facts).outcome());
        assertEquals(EchoWinPolicy.Reason.TIME_EXPIRED, check(EchoPhase.OPERATIONS,0,false,facts).reason());
        facts.set(2, new EchoWinPolicy.PlayerFact(EchoFaction.CARRIER, EchoLifeState.WOUNDED,false,false));
        assertEquals(EchoWinPolicy.Outcome.NONE, check(EchoPhase.OPERATIONS,20,false,facts).outcome());
        facts.set(2,fact(EchoFaction.CARRIER, EchoLifeState.HEALTHY));
        facts.set(3,new EchoWinPolicy.PlayerFact(EchoFaction.INTERCEPTOR, EchoLifeState.HEALTHY,false,false));
        assertEquals(EchoWinPolicy.Reason.NO_INTERCEPTOR, check(EchoPhase.OPERATIONS,20,false,facts).reason());
    }

    @Test void independentContractIsNotMainTeamAndScavengerMustStandOnline() {
        var win = new EchoWinPolicy.Decision(EchoWinPolicy.Outcome.INTERCEPTORS,EchoWinPolicy.Reason.TIME_EXPIRED);
        var deadBroker = fact(EchoFaction.BROKER,EchoLifeState.ELIMINATED);
        assertTrue(EchoWinPolicy.didPlayerWin(win,deadBroker,EchoContract.KEY_DEALER,true,0));
        assertFalse(EchoWinPolicy.didPlayerWin(win,deadBroker,EchoContract.SCAVENGER,true,3));
        assertFalse(EchoWinPolicy.didPlayerWin(win,fact(EchoFaction.BROKER,EchoLifeState.DOWNED),EchoContract.SCAVENGER,false,3));
        assertTrue(EchoWinPolicy.didPlayerWin(win,fact(EchoFaction.BROKER,EchoLifeState.WOUNDED),EchoContract.SCAVENGER,false,3));
        assertFalse(EchoWinPolicy.didPlayerWin(new EchoWinPolicy.Decision(EchoWinPolicy.Outcome.VOID,EchoWinPolicy.Reason.ABORTED),
                deadBroker,EchoContract.KEY_DEALER,true,0));
        assertFalse(EchoWinPolicy.didPlayerWin(win,fact(EchoFaction.CARRIER,EchoLifeState.HEALTHY),null,false,0));
    }
}
