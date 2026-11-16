package io.wifi.srepokemoncompat.sweep;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import static org.junit.jupiter.api.Assertions.*;

public class PokemonSweepQueryTest {
    public static class Data {
        boolean playerOwned;
        public boolean isPlayerOwned() { return playerOwned; }
    }
    public static class Pokemon {
        public boolean battling;
        public Data data = new Data();
        public boolean isBattling() { return battling; }
        public Data getPokemon() { return data; }
    }
    public static class MissingApi { }
    public static class BrokenGetter extends Pokemon {
        @Override public Data getPokemon() { throw new IllegalStateException("broken getter"); }
    }

    @Test void liveBattleAndPlayerOwnershipAreReadEveryTime() throws Exception {
        var query = PokemonSweepQuery.bind(Pokemon.class);
        var pokemon = new Pokemon();
        assertFalse(query.test(pokemon));
        pokemon.battling = true; assertTrue(query.test(pokemon));
        pokemon.battling = false; assertFalse(query.test(pokemon));
        pokemon.data.playerOwned = true; assertTrue(query.test(pokemon));
        pokemon.data.playerOwned = false; assertFalse(query.test(pokemon));
    }
    @Test void battleProtectionDoesNotDependOnOwnerBeingReadable() throws Exception {
        var pokemon = new BrokenGetter(); pokemon.battling = true;
        assertTrue(PokemonSweepQuery.bind(Pokemon.class).test(pokemon));
        pokemon.battling = false;
        assertThrows(IllegalStateException.class, () -> PokemonSweepQuery.bind(Pokemon.class).test(pokemon));
    }
    @Test void npcAndWildStateDoesNotBecomePlayerOwned() throws Exception {
        var pokemon = new Pokemon();
        assertFalse(PokemonSweepQuery.bind(Pokemon.class).test(pokemon));
        assertNull(PokemonSweepQuery.protectedState(pokemon));
        assertNull(PokemonSweepQuery.protectedState("not a Pokemon"));
    }
    @Test void missingApiFailsBinding() {
        assertThrows(NoSuchMethodException.class, () -> PokemonSweepQuery.bind(MissingApi.class));
    }
    @Test void realClassNameDiscoveryAndUnsupportedApiFailClosed() throws Exception {
        for (Class<?> source : new Class<?>[]{Pokemon.class, MissingApi.class}) {
            String name = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";
            var writer = new ClassWriter(0);
            try (var in = getClass().getResourceAsStream("/" + Type.getInternalName(source) + ".class")) {
                assertNotNull(in);
                new ClassReader(in).accept(new ClassRemapper(writer,
                        new SimpleRemapper(Type.getInternalName(source), name.replace('.', '/'))), 0);
            }
            var type = new ClassLoader(getClass().getClassLoader()) {
                Class<?> define() { var bytes = writer.toByteArray(); return defineClass(null, bytes, 0, bytes.length); }
            }.define();
            Object entity = type.getConstructor().newInstance();
            if (source == MissingApi.class) assertEquals(true, PokemonSweepQuery.protectedState(entity));
            else {
                assertEquals(false, PokemonSweepQuery.protectedState(entity));
                type.getField("battling").setBoolean(entity, true);
                assertEquals(true, PokemonSweepQuery.protectedState(entity));
            }
        }
    }
}
