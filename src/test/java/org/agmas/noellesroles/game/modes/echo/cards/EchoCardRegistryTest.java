package org.agmas.noellesroles.game.modes.echo.cards;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.cards.EchoCardDefinition.*;

class EchoCardRegistryTest {
    @Test void allDefinitionsHaveOneRecipeAndPositiveWeights() {
        assertEquals(37,EchoCardRegistry.all().size());
        assertEquals(21,EchoCardRegistry.all().stream().filter(c -> c.tier()==Tier.BASE).count());
        assertEquals(16,EchoCardRegistry.recipes().size());
        for(var card:EchoCardRegistry.all()) {
            assertTrue(card.weight()>0);
            assertFalse(card.params().isEmpty());
            assertTrue(card.icon().endsWith(card.shortId()+".png"));
            if(card.tier()==Tier.BASE) {
                assertTrue(EchoCardRegistry.craftOutput(UUID.randomUUID(),card.id(),UUID.randomUUID(),card.id(),card.drawFaction()).isPresent());
            }
        }
        assertThrows(UnsupportedOperationException.class,() -> EchoCardRegistry.all().clear());
    }

    @Test void rejectsSameInstanceCrossSeriesWrongFactionAndAdvancedInputs() {
        UUID id=UUID.randomUUID();
        String dash="noellesroles:echo/short_dash", advanced="noellesroles:echo/long_dash", radar="noellesroles:echo/team_radar";
        assertTrue(EchoCardRegistry.craftOutput(id,dash,id,dash,EchoFaction.CARRIER).isEmpty());
        assertTrue(EchoCardRegistry.craftOutput(id,dash,UUID.randomUUID(),radar,EchoFaction.CARRIER).isEmpty());
        assertTrue(EchoCardRegistry.craftOutput(id,dash,UUID.randomUUID(),dash,EchoFaction.BROKER).isEmpty());
        assertTrue(EchoCardRegistry.craftOutput(id,advanced,UUID.randomUUID(),advanced,EchoFaction.CARRIER).isEmpty());
        for(var faction:EchoFaction.values()) assertTrue(EchoCardRegistry.craftOutput(id,"noellesroles:echo/door_wedge",
                UUID.randomUUID(),"noellesroles:echo/door_wedge",faction).orElseThrow().isKey());
    }
}
