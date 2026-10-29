package org.agmas.noellesroles.game.modes.echo.specialization;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRuleRepository;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EchoSpecializationTest {
    @Test void everyFactionHasExactlyOneStarterAmongTwoOptions() {
        for (var faction : EchoFaction.values()) {
            var options = EchoSpecialization.of(faction);
            assertEquals(2, options.size(), faction.name());
            assertTrue(options.stream().allMatch(s -> s.faction() == faction));
            assertEquals(1, options.stream().filter(EchoSpecialization::starter).count(), faction.name());
            assertEquals(options.getFirst(), EchoSpecialization.starter(faction));
        }
        assertEquals(EchoSpecialization.PORTER, EchoSpecialization.starter(null));
        assertEquals(EchoSpecialization.HUNTER, EchoSpecialization.byId("hunter").orElseThrow());
        assertTrue(EchoSpecialization.byId("unknown").isEmpty());
    }

    @Test void combatScalingOnlyTouchesCutterNumbersAndKeepsOtherFields() {
        var rules = EchoRuleConfig.DEFAULT;
        var hunter = EchoSpecialization.HUNTER.combat(rules);
        assertEquals(EchoSpecialization.scaled(rules.combat().cutterChargeTicks(), 0.85), hunter.cutterChargeTicks());
        assertEquals(EchoSpecialization.scaled(rules.combat().cutterCooldownTicks(), 0.9), hunter.cutterCooldownTicks());
        assertEquals(rules.combat().bleedTicks(), hunter.bleedTicks());
        assertEquals(rules.combat().rescueTicks(), hunter.rescueTicks());
        assertEquals(rules.combat(), EchoSpecialization.PORTER.combat(rules));
        var saboteur = EchoSpecialization.SABOTEUR.combat(rules);
        assertEquals(EchoSpecialization.scaled(rules.combat().cutterCooldownTicks(), 1.2), saboteur.cutterCooldownTicks());
    }

    @Test void scalingNeverProducesZeroTicksAndUsesRounding() {
        assertEquals(1, EchoSpecialization.scaled(1, 0.5));
        assertEquals(19, EchoSpecialization.scaled(24, 0.8));
        assertEquals(12, EchoSpecialization.scaled(12, 1));
    }

    @Test void ruleOverridesApplyAndInvalidValuesAreRejected() throws Exception {
        var parsed = EchoRuleRepository.parse("{\"specialization\":{\"porter\":{\"cargoCapacityBonus\":0,\"cargoPenaltyDelta\":0},"
                + "\"scout\":{\"collectSpeed\":1.2},\"fixer\":{\"tradeRangeBonus\":3,\"proposalCooldownSpeed\":0.9}}}");
        assertEquals(0, parsed.specialization().porter().cargoCapacityBonus());
        assertEquals(1.2, parsed.specialization().scout().collectSpeed());
        assertEquals(3, parsed.specialization().fixer().tradeRangeBonus());
        for (String bad : List.of(
                "{\"specialization\":{\"porter\":{\"cargoCapacityBonus\":2}}}",
                "{\"specialization\":{\"scout\":{\"collectSpeed\":0.4}}}",
                "{\"specialization\":{\"smuggler\":{\"blackCollectSpeed\":2}}}",
                "{\"specialization\":{\"hunter\":{\"unknownField\":1}}}",
                "{\"specialization\":{\"fixer\":{\"tradeRangeBonus\":5}}}",
                "{\"specialization\":{\"porter\":{\"cargoPenaltyDelta\":0.2}}}"))
            assertThrows(IOException.class, () -> EchoRuleRepository.parse(bad), bad);
        assertEquals(EchoRuleConfig.DEFAULT, EchoRuleRepository.parse("{}"));
        assertEquals(EchoRuleConfig.DEFAULT, EchoRuleRepository.parse(EchoRuleRepository.example()));
        assertEquals(EchoRuleConfig.DEFAULT, EchoRuleRepository.parse(EchoRuleRepository.json(EchoRuleConfig.DEFAULT)));
    }

    @Test void payloadRejectsOversizedOrMismatchedOptions() {
        var payload = EchoSpecializationPayload.of(java.util.UUID.randomUUID(), EchoFaction.BROKER, "SMUGGLER", 360, "smuggler", false);
        assertEquals(2, payload.options().size());
        assertEquals("smuggler", payload.options().getFirst().id());
        assertThrows(IllegalArgumentException.class, () -> new EchoSpecializationPayload(java.util.UUID.randomUUID(),
                EchoFaction.CARRIER, "", 360, List.of(payload.options().getFirst(), payload.options().getFirst(),
                payload.options().getFirst()), "porter", false));
        assertThrows(IllegalArgumentException.class, () -> new EchoSpecializationSelectPayload(java.util.UUID.randomUUID(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EchoSpecializationSelectPayload(java.util.UUID.randomUUID(), 1, 2));
    }
}
