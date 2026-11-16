package io.wifi.srepackcompat.config;

import io.wifi.srepokemoncompat.PokemonConfigInstaller;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.api.metadata.ModDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonJavaOverrideTest {
    @TempDir Path temp;

    private Path root() throws Exception {
        Path root = temp.resolve("mod");
        Files.createDirectories(root.resolve("config"));
        try (var in = getClass().getResourceAsStream("/config/fabric_loader_dependencies.json")) {
            assertNotNull(in);
            Files.copy(in, root.resolve("config/fabric_loader_dependencies.json"));
        }
        return root;
    }

    @Test void actualLoaderReplacesOnlyJavaAndAcceptsOnly21And25() throws Exception {
        Path config = temp.resolve("game/config");
        assertEquals(1, PokemonConfigInstaller.install(List.of(root()), config));
        var overrides = new DependencyOverrides(config);
        var dependencies = new ArrayList<ModDependency>();
        dependencies.add(new net.fabricmc.loader.impl.metadata.ModDependencyImpl(
                ModDependency.Kind.DEPENDS, "java", List.of("21")));
        dependencies.add(new net.fabricmc.loader.impl.metadata.ModDependencyImpl(
                ModDependency.Kind.DEPENDS, "minecraft", List.of("1.21.1")));
        var result = apply(overrides, dependencies, "cobblemon");
        assertEquals(2, result.size());
        var java = result.stream().filter(d -> d.getModId().equals("java")).findFirst().orElseThrow();
        for (String v : List.of("21", "25")) assertTrue(java.matches(Version.parse(v)), v);
        for (String v : List.of("17", "22", "24", "26")) assertFalse(java.matches(Version.parse(v)), v);
        assertEquals(dependencies, apply(overrides, dependencies, "unrelated"));
        assertTrue(result.stream().filter(d -> d.getModId().equals("minecraft"))
                .findFirst().orElseThrow().matches(Version.parse("1.21.1")));
    }

    @Test void preservesOtherOverridesAndDoesNotRewriteOnSecondInstall() throws Exception {
        Path config = Files.createDirectories(temp.resolve("game/config"));
        Path target = config.resolve("fabric_loader_dependencies.json");
        Files.writeString(target, """
                {"version":1,"overrides":{"other":{"+depends":{"java":">=17"}},
                "cobblemon":{"+depends":{"fabricloader":">=0.17.2"},"breaks":{"badmod":"*"}}}}
                """);
        Path root = root();
        assertEquals(1, PokemonConfigInstaller.install(List.of(root), config));
        var overrides = JsonParser.parseString(Files.readString(target)).getAsJsonObject().getAsJsonObject("overrides");
        assertEquals(">=17", overrides.getAsJsonObject("other").getAsJsonObject("+depends").get("java").getAsString());
        assertEquals(">=0.17.2", overrides.getAsJsonObject("cobblemon").getAsJsonObject("+depends").get("fabricloader").getAsString());
        assertTrue(overrides.getAsJsonObject("cobblemon").has("breaks"));
        assertEquals(0, PokemonConfigInstaller.install(List.of(root), config));
    }

    @Test void refusesMalformedOrConflictingOverridesWithoutReplacingFile() throws Exception {
        Path config = Files.createDirectories(temp.resolve("game/config"));
        Path target = config.resolve("fabric_loader_dependencies.json");
        Path root = root();
        for (String text : List.of("not json", "{\"version\":2}",
                "{\"version\":1,\"overrides\":{\"cobblemon\":{\"depends\":{\"java\":\"21\"}}}}")) {
            Files.writeString(target, text);
            assertThrows(java.io.IOException.class, () -> PokemonConfigInstaller.install(List.of(root), config));
            assertEquals(text, Files.readString(target));
        }
    }
    @SuppressWarnings("unchecked")
    private static java.util.Collection<ModDependency> apply(DependencyOverrides overrides,
            java.util.Collection<ModDependency> dependencies, String id) {
        var current = new java.util.concurrent.atomic.AtomicReference<>(dependencies);
        var type = net.fabricmc.loader.impl.metadata.LoaderModMetadata.class;
        var metadata = (net.fabricmc.loader.impl.metadata.LoaderModMetadata) java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getDependencies" -> current.get();
                    case "setDependencies" -> { current.set((java.util.Collection<ModDependency>) args[0]); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        overrides.apply(metadata);
        return current.get();
    }
}
