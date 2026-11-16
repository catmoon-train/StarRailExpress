package io.wifi.srepokemoncompat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PokemonModuleTest {
    @Test void moduleOwnsAllPokemonConfigurations() throws Exception {
        try (var in = getClass().getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in);
            var mod = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("sre_pokemon_compat", mod.get("id").getAsString());
            assertEquals("*", mod.get("environment").getAsString());
            assertEquals(7, mod.getAsJsonArray("mixins").size());
            for (var entry : mod.getAsJsonArray("mixins")) {
                String config = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("config").getAsString();
                assertNotNull(getClass().getResource("/" + config), config);
            }
        }
    }
    @Test void queuedSpawnOnlyGatesWithoutSecondRateReduction() throws Exception {
        try (var in = getClass().getResourceAsStream("/io/wifi/srepackcompat/mixin/spawning/QueuedSpawnBudgetMixin.class")) {
            assertNotNull(in);
            var node = new ClassNode(); new ClassReader(in).accept(node, 0);
            for (var method : node.methods) for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) assertFalse(call.owner.endsWith("CobblemonSpawnBudget") && call.name.equals("scale"));
            }
        }
    }
}
