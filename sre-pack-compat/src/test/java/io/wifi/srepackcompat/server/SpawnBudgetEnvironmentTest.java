package io.wifi.srepackcompat.server;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SpawnBudgetEnvironmentTest {
    @Test void onlyGenericTrackingIsCommonAndClientConfigsStayClientOnly() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(stream);
            var mod = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("*", mod.get("environment").getAsString());
            var clientConfigs = new HashSet<String>();
            int common = 0;
            for (var entry : mod.getAsJsonArray("mixins")) {
                if (entry.isJsonPrimitive()) {
                    assertEquals("sre-pack-compat-server.mixins.json", entry.getAsString());
                    common++;
                } else {
                    assertEquals("client", entry.getAsJsonObject().get("environment").getAsString());
                    clientConfigs.add(entry.getAsJsonObject().get("config").getAsString());
                }
            }
            assertEquals(1, common);
            assertEquals(Set.of("sre-pack-compat.mixins.json", "sre-pack-compat-particles.mixins.json", "sre-pack-compat-swap.mixins.json",
                    "sre-pack-compat-chloride.mixins.json", "sre-pack-compat-settings.mixins.json", "sre-pack-compat-runtime.mixins.json",
                    "sre-pack-compat-jei.mixins.json"), clientConfigs);
            assertFalse(mod.getAsJsonObject("entrypoints").has("main"));
        }
    }
    @Test void preLaunchReturnsOnServerBeforeAnyClientWork() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("io/wifi/srepackcompat/SrePackCompatPreLaunch.class")) {
            assertNotNull(stream);
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0);
            var method = node.methods.stream().filter(m -> m.name.equals("onPreLaunch")).findFirst().orElseThrow();
            int environment = -1, earlyReturn = -1, firstClientWork = Integer.MAX_VALUE;
            for (int i = 0; i < method.instructions.size(); i++) {
                var insn = method.instructions.get(i);
                if (insn instanceof MethodInsnNode call) {
                    if (call.name.equals("getEnvironmentType")) environment = i;
                    if (call.owner.contains("ObsoleteModRemoval") || call.owner.contains("BundledConfigInstaller")
                            || call.owner.contains("StartupClassCache")) firstClientWork = Math.min(firstClientWork, i);
                }
                if (insn.getOpcode() == Opcodes.RETURN && earlyReturn < 0) earlyReturn = i;
            }
            assertTrue(environment >= 0 && environment < earlyReturn && earlyReturn < firstClientWork);
        }
    }
}
