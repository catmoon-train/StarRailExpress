package io.wifi.srepackcompat.cobblemon;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class EvolutionRequirementQueryTest {
    public static class Walking { }
    public static class SpecialWalking extends Walking { }
    public static class Evolution {
        final Set<Object> requirements = new LinkedHashSet<>();
        public Set<Object> getRequirements() { return requirements; }
    }
    public static class Form {
        final Set<Evolution> evolutions = new LinkedHashSet<>();
        public Set<Evolution> getEvolutions() { return evolutions; }
    }
    public static class Pokemon {
        Form form = new Form();
        public Form getForm() { return form; }
        public Iterable<Evolution> getEvolutions() { throw new AssertionError("Must not sort evolutions"); }
    }

    private EvolutionRequirementQuery query() throws Exception {
        return EvolutionRequirementQuery.bind(Pokemon.class, Form.class, Evolution.class, Walking.class);
    }

    @Test void emptyEvolutionsReturnFalse() throws Exception {
        assertFalse(query().hasWalkingRequirement(new Pokemon()));
    }

    @Test void unrelatedAndEmptyRequirementsReturnFalse() throws Exception {
        var pokemon = new Pokemon();
        var evolution = new Evolution();
        evolution.requirements.add("other");
        pokemon.form.evolutions.add(evolution);
        pokemon.form.evolutions.add(new Evolution());
        assertFalse(query().hasWalkingRequirement(pokemon));
    }

    @Test void laterEvolutionAndRequirementSubclassAreRecognized() throws Exception {
        var pokemon = new Pokemon();
        pokemon.form.evolutions.add(new Evolution());
        var evolution = new Evolution();
        evolution.requirements.add("other");
        evolution.requirements.add(new SpecialWalking());
        pokemon.form.evolutions.add(evolution);
        assertTrue(query().hasWalkingRequirement(pokemon));
    }

    @Test void formReplacementIsImmediatelyVisible() throws Exception {
        var pokemon = new Pokemon();
        var query = query();
        assertFalse(query.hasWalkingRequirement(pokemon));
        pokemon.form = new Form();
        var evolution = new Evolution();
        evolution.requirements.add(new Walking());
        pokemon.form.evolutions.add(evolution);
        assertTrue(query.hasWalkingRequirement(pokemon));
        pokemon.form = new Form();
        assertFalse(query.hasWalkingRequirement(pokemon));
    }

    @Test void inPlaceMutationIsImmediatelyVisible() throws Exception {
        var pokemon = new Pokemon();
        var evolution = new Evolution();
        pokemon.form.evolutions.add(evolution);
        var query = query();
        assertFalse(query.hasWalkingRequirement(pokemon));
        evolution.requirements.add(new Walking());
        assertTrue(query.hasWalkingRequirement(pokemon));
        evolution.requirements.clear();
        assertFalse(query.hasWalkingRequirement(pokemon));
        pokemon.form.evolutions.clear();
        assertFalse(query.hasWalkingRequirement(pokemon));
    }

    @Test void matchShortCircuitsBeforeLaterGetters() throws Exception {
        var pokemon = new Pokemon();
        var first = new Evolution();
        first.requirements.add(new Walking());
        pokemon.form.evolutions.add(first);
        pokemon.form.evolutions.add(new Evolution() {
            @Override public Set<Object> getRequirements() { throw new AssertionError("Not needed"); }
        });
        assertTrue(query().hasWalkingRequirement(pokemon));
    }

    @Test void getterErrorsAreNotSwallowed() throws Exception {
        var pokemon = new Pokemon() {
            @Override public Form getForm() { throw new IllegalStateException("broken data"); }
        };
        assertEquals("broken data", assertThrows(IllegalStateException.class,
                () -> query().hasWalkingRequirement(pokemon)).getMessage());
    }

    @Test void missingInterfaceFailsBindingAndAbsentModFallsBack() {
        assertThrows(NoSuchMethodException.class,
                () -> EvolutionRequirementQuery.bind(Object.class, Form.class, Evolution.class, Walking.class));
        assertNull(EvolutionRequirementQuery.query(new Pokemon()));
    }
}
