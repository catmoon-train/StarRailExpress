package io.wifi.srepokemoncompat.sweep;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class PokemonSweepQueryTest {
    private static final String POKEMON = "com/cobblemon/mod/common/entity/pokemon/PokemonEntity";

    @Test void wildNpcAndOwnedPokemonAreProtectedInAndOutOfBattle() throws Exception {
        Class<?> type = pokemonType(false);
        Object pokemon = type.getConstructor().newInstance();
        // 不提供 getPokemon / isBattling 等查询方法，保护不依赖它们是否存在、可用。
        for (boolean battling : new boolean[]{false, true, false}) {
            for (String owner : new String[]{"wild", "npc", "player"}) {
                type.getField("battling").setBoolean(pokemon, battling);
                type.getField("owner").set(pokemon, owner);
                assertEquals(Boolean.TRUE, PokemonSweepQuery.protectedState(pokemon), owner + " battling=" + battling);
            }
        }
    }

    @Test void pokemonSubclassesAreAlsoProtected() throws Exception {
        Object pokemon = pokemonType(true).getConstructor().newInstance();
        assertEquals(Boolean.TRUE, PokemonSweepQuery.protectedState(pokemon));
    }

    @Test void unrelatedEntitiesAndNullDoNotBecomePokemon() {
        assertNull(PokemonSweepQuery.protectedState(new Object()));
        assertNull(PokemonSweepQuery.protectedState("PokemonEntity"));
        assertNull(PokemonSweepQuery.protectedState(null));
    }

    private static Class<?> pokemonType(boolean subclass) {
        return new ClassLoader(PokemonSweepQueryTest.class.getClassLoader()) {
            Class<?> define(String name, String parent) {
                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, parent, null);
                writer.visitField(Opcodes.ACC_PUBLIC, "battling", "Z", null, null).visitEnd();
                writer.visitField(Opcodes.ACC_PUBLIC, "owner", "Ljava/lang/String;", null, null).visitEnd();
                var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                constructor.visitCode();
                constructor.visitVarInsn(Opcodes.ALOAD, 0);
                constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
                constructor.visitInsn(Opcodes.RETURN);
                constructor.visitMaxs(0, 0);
                constructor.visitEnd();
                writer.visitEnd();
                byte[] bytes = writer.toByteArray();
                return defineClass(null, bytes, 0, bytes.length);
            }
            Class<?> create() {
                Class<?> root = define(POKEMON, "java/lang/Object");
                return subclass ? define("test/CustomPokemon", POKEMON) : root;
            }
        }.create();
    }
}
