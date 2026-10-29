package org.agmas.noellesroles.game.modes.echo.cards;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.*;

class EchoDraftServiceTest {
    @Test void rarityGuaranteeDistinctDefinitionsAndNoWrongFaction() {
        for(var faction:EchoFaction.values()) for(var quality:PackQuality.values()) for(int seed=0;seed<100;seed++) {
            var offer=draw(quality,Context.all(faction,false,true),new Random(seed));
            assertEquals(3,offer.candidates().stream().map(EchoCardDefinition::id).distinct().count());
            assertTrue(offer.candidates().stream().allMatch(c -> c.drawFaction()==faction && !c.isKey()));
            if(quality!=PackQuality.COMMON) assertNotEquals(EchoCardDefinition.Rarity.COMMON,offer.candidates().getFirst().rarity());
            if(quality==PackQuality.RARE) assertEquals(EchoCardDefinition.Rarity.RARE,offer.candidates().getFirst().rarity());
        }
    }

    @Test void keyInAnySlotReservesOneAndInsufficientPoolDoesNotReturnHalfOffer() {
        var key=EchoCardRegistry.require("noellesroles:echo/sound_key");
        var dash=EchoCardRegistry.require("noellesroles:echo/short_dash");
        var radar=EchoCardRegistry.require("noellesroles:echo/team_radar");
        assertTrue(new Offer(java.util.List.of(dash,key,radar),true).requiresKeyReservation());
        assertThrows(IllegalStateException.class,() -> draw(PackQuality.RARE,
                new Context(EchoFaction.CARRIER,Set.of(dash.id(),key.id()),true,true),new Random(1)));
        assertEquals(draw(PackQuality.BLACK_MARKET,Context.all(EchoFaction.CARRIER,true,true),new Random(9)),
                draw(PackQuality.BLACK_MARKET,Context.all(EchoFaction.CARRIER,true,true),new Random(9)));
    }

    @Test void boundCandidateCannotRerollOrTransferAndOfferCloseDoesNotConsumeTwice() {
        UUID owner=UUID.randomUUID();
        var pack=new EchoPackDraft(UUID.randomUUID(),PackQuality.RARE);
        assertFalse(pack.begin(owner,0,false,Context.all(EchoFaction.CARRIER,true,true),new Random(1)));
        assertTrue(pack.begin(owner,0,true,Context.all(EchoFaction.CARRIER,true,true),new Random(1)));
        assertTrue(pack.visibleCandidates(owner).isEmpty());
        assertTrue(pack.cancel(owner,10));
        assertFalse(pack.consumed());
        assertFalse(pack.begin(owner,209,true,Context.all(EchoFaction.CARRIER,true,true),new Random(22)));
        assertFalse(pack.begin(UUID.randomUUID(),210,true,Context.all(EchoFaction.CARRIER,true,true),new Random(22)));
        assertTrue(pack.begin(owner,210,true,Context.all(EchoFaction.CARRIER,true,true),new Random(22)));
        pack.advance(240);
        var expected=draw(PackQuality.RARE,Context.all(EchoFaction.CARRIER,true,true),new Random(1));
        assertEquals(expected.candidates(),pack.visibleCandidates(owner));
        pack.close(owner,250);
        assertEquals(EchoPackDraft.State.OFFERED,pack.state());
        assertTrue(pack.consumed());
        assertEquals(expected.candidates().getFirst(),pack.advance(640).orElseThrow().chosen());
        assertTrue(pack.choose(owner,1,640).isEmpty());
        assertTrue(pack.advance(700).isEmpty());
        assertFalse(pack.requiresKeyReservation());
    }
}
