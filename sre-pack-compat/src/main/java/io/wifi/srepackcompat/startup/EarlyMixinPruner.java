package io.wifi.srepackcompat.startup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 提前剔除已由 SRE 无条件取消的已知普通补丁，原 MixinSquared 取消器仍保留。 */
public final class EarlyMixinPruner {
    private static final Logger LOGGER = LoggerFactory.getLogger("sre-pack-compat/mixin-startup");
    private static final String PREFIX = "org.spongepowered.asm.mixin.transformer.";
    private static final Map<String, String> IMPLEMENTATIONS = Map.of(
            "MixinConfig", "836b27b75a34da8842771dcaa48cc25d8beca20d3a737fd0cd561e7d4c8765f3",
            "MixinProcessor", "47baebf8ef42cc3dc4099eee55197b681445014fb7eb3c19c5f9f702e9d7b5a4",
            "MixinTransformer", "8258772688586aae44f8861cdf757aad52064bb9aea8ee2d60601c819f1d2cab");
    private record Target(String id, String version, String config, String packageName,
                          String configHash, String plugin, String pluginHash) { }
    private static final List<Target> TARGETS = List.of(
            new Target("wathe", "1.3.2-1.21.1", "wathe.mixins.json", "dev.doctor4t.wathe.mixin",
                    "c99b713394c9015121c2243a9f285bd03663acde5c4dd44d00e61db3f2a02169",
                    "dev.doctor4t.wathe.mixin.TmmMixinPlugin", "c2a949828e914a4cafe3d35a166dfb003d0a2007fcb1a4bb7e8192780de9af26"),
            new Target("ratatouille", "1.4.3-1.21.1", "ratatouille.mixins.json", "dev.doctor4t.ratatouille.mixin",
                    "eed72770e9f1982c93815f2b6b86551629cab42085affbdfd8de35fe59231ffc", null, null),
            new Target("sodium-extra", "0.9.5+mc1.21.1", "sodium-extra.mixins.json", "me.flashyreese.mods.sodiumextra.mixin",
                    "a4b862370955c916344988d267d0505f80d8224d41bf0128d9078478ba184e1b",
                    "me.flashyreese.mods.sodiumextra.mixin.SodiumExtraMixinConfigPlugin",
                    "cc3b9ef989969d85269afb84dfd9430a33ed001e59535dfbdbfe4c7d4f300fb4"));
    private static boolean attempted;

    public static synchronized void install() {
        if (attempted) return;
        attempted = true;
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT || loader.isDevelopmentEnvironment()) return;
        long started = System.nanoTime();
        try {
            if (!enabled(loader.getConfigDir().resolve("sre-pack-compat-mixin.properties"))) {
                LOGGER.info("Early SRE mixin pruning disabled by config");
                return;
            }
            if (!loader.getModContainer("mixinsquared").map(mod -> mod.getMetadata().getVersion()
                    .getFriendlyString().equals("0.3.7-beta.1")).orElse(false)) {
                throw new IOException("Unverified MixinSquared version");
            }
            ModContainer core = loader.getModContainer("starrailexpress").orElse(null);
            if (core == null) return;
            JsonObject metadata;
            try (var reader = Files.newBufferedReader(core.findPath("fabric.mod.json").orElseThrow(), StandardCharsets.UTF_8)) {
                metadata = JsonParser.parseReader(reader).getAsJsonObject();
            }
            SreMixinCancellationPolicy policy = new SreMixinCancellationPolicy(
                    verifiedCanceller(core, metadata, "io.wifi.WatheMixinBlocker",
                            "68856939e06b9dee799fff368cda20e4d6ec51e5ff3ccae8bb373b5a945b6f9b"),
                    verifiedCanceller(core, metadata, "io.wifi.SodiumExtraLightOptionMixinBlocker",
                            "6d9295f1f1f723edec0e8d8cc4b7d7e84cc58229765c1715df5ead6d4f2ae658"));
            if (!policy.watheRule() && !policy.sodiumExtraRule()) throw new IOException("Unverified SRE cancellers");
            ClassLoader mixinLoader = MixinEnvironment.class.getClassLoader();
            for (var entry : IMPLEMENTATIONS.entrySet()) {
                if (!digest(resource(mixinLoader, PREFIX + entry.getKey())).equals(entry.getValue())) {
                    throw new IOException("Unverified Mixin implementation: " + entry.getKey());
                }
            }
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer == null || !transformer.getClass().getName().equals(PREFIX + "MixinTransformer")) {
                throw new IOException("Active Mixin transformer already customized");
            }
            Map<String, Set<String>> verified = verifiedDeclarations(loader, policy);
            Object processor = accessible(transformer.getClass(), "processor").get(transformer);
            if (!processor.getClass().getName().equals(PREFIX + "MixinProcessor")) throw new IOException("Custom Mixin processor");
            List<Object> configs = new ArrayList<>((List<?>) accessible(processor.getClass(), "pendingConfigs").get(processor));
            // selectConfigs 先从全局队列移除、onLoad，再加入 pendingConfigs；两边都要覆盖。
            for (Object handle : Mixins.getConfigs()) {
                var get = handle.getClass().getDeclaredMethod("get");
                if (!get.trySetAccessible()) throw new IllegalAccessException("Config.get");
                configs.add(get.invoke(handle));
            }
            Class<?> configType = Class.forName(PREFIX + "MixinConfig", false, mixinLoader);
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            List<PendingMixinPruner.ConfigView> views = new ArrayList<>();
            for (Object config : configs) {
                if (config == null || !seen.add(config)) continue;
                if (config.getClass() != configType) throw new IOException("Custom Mixin config");
                views.add(new ReflectiveConfig(config, configType));
            }
            PendingMixinPruner.Result result = PendingMixinPruner.apply(views, verified, policy);
            LOGGER.info("Early SRE mixin pruning: removed={}, configs={}, elapsedMs={}; original cancellers retained",
                    result.removed().size(), result.configs(), (System.nanoTime() - started) / 1_000_000);
            for (Target target : TARGETS) {
                LOGGER.info("Early SRE mixin pruning {}: {}", target.id(), result.removed().stream()
                        .filter(n -> n.startsWith(target.packageName() + ".")).toList());
            }
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError error) {
            LOGGER.warn("Early SRE mixin pruning not installed; using original MixinSquared cancellation: {}", error.toString());
        }
    }

    static boolean enabled(Path file) throws IOException {
        if (Files.notExists(file)) return false;
        Properties config = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { config.load(reader); }
        String value = config.getProperty("enabled", "false").trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IOException("Invalid enabled option");
        return Boolean.parseBoolean(value);
    }

    static boolean registered(JsonObject metadata, String name) {
        if (!metadata.has("entrypoints") || !metadata.getAsJsonObject("entrypoints").has("mixinsquared")) return false;
        for (var entry : metadata.getAsJsonObject("entrypoints").getAsJsonArray("mixinsquared")) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString() && name.equals(entry.getAsString())) return true;
        }
        return false;
    }

    private static boolean verifiedCanceller(ModContainer core, JsonObject metadata, String name, String hash) throws IOException {
        if (!registered(metadata, name)) return false;
        var path = core.findPath(name.replace('.', '/') + ".class");
        return path.isPresent() && hash.equals(digest(Files.readAllBytes(path.get())))
                && hash.equals(digest(resource(EarlyMixinPruner.class.getClassLoader(), name)));
    }

    private static Map<String, Set<String>> verifiedDeclarations(FabricLoader loader, SreMixinCancellationPolicy policy) throws IOException {
        Properties known = new Properties();
        try (var stream = EarlyMixinPruner.class.getResourceAsStream("/sre-pack-compat-early-mixins.properties")) {
            if (stream == null) throw new IOException("Missing verified Mixin manifest");
            known.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (Target target : TARGETS) {
            ModContainer mod = loader.getModContainer(target.id()).orElse(null);
            if (mod == null || !mod.getMetadata().getVersion().getFriendlyString().equals(target.version())) continue;
            var configPath = mod.findPath(target.config());
            if (configPath.isEmpty() || !target.configHash().equals(digest(Files.readAllBytes(configPath.get())))) continue;
            if (target.plugin() != null && !target.pluginHash().equals(digest(resource(EarlyMixinPruner.class.getClassLoader(), target.plugin())))) continue;
            if (target.id().equals("sodium-extra") && !"ca7227d12ddfc1f8e67cf4598c8f02010e333ca77369feedf482871887f1bcd0"
                    .equals(digest(resource(EarlyMixinPruner.class.getClassLoader(),
                            "net.caffeinemc.caffeineconfig.AbstractCaffeineConfigMixinPlugin")))) continue;
            Set<String> names = new HashSet<>();
            for (String name : known.stringPropertyNames()) {
                if (!name.startsWith(target.packageName() + ".") || !policy.cancels(name)) continue;
                byte[] bytes = resource(EarlyMixinPruner.class.getClassLoader(), name);
                if (verifiedMixin(bytes, known.getProperty(name))) names.add(name);
            }
            result.put(target.config(), names);
        }
        return result;
    }

    static boolean verifiedMixin(byte[] bytes, String expected) {
        return digest(bytes).equals(expected) && simpleMixin(bytes);
    }

    /** 不提前移除任何可加载接口、accessor、嵌套类或非 Object 继承层级。 */
    static boolean simpleMixin(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if ((node.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_RECORD)) != 0
                || !"java/lang/Object".equals(node.superName) || !node.interfaces.isEmpty()
                || node.outerClass != null || node.nestHostClass != null
                || node.nestMembers != null && !node.nestMembers.isEmpty() || !node.innerClasses.isEmpty()) return false;
        for (var method : node.methods) {
            if (hasAnnotation(method.visibleAnnotations, "Lorg/spongepowered/asm/mixin/gen/Accessor;")
                    || hasAnnotation(method.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/gen/Accessor;")
                    || hasAnnotation(method.visibleAnnotations, "Lorg/spongepowered/asm/mixin/gen/Invoker;")
                    || hasAnnotation(method.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/gen/Invoker;")) return false;
        }
        return hasAnnotation(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;")
                || hasAnnotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String desc) {
        return annotations != null && annotations.stream().anyMatch(a -> a.desc.equals(desc));
    }

    private static byte[] resource(ClassLoader loader, String name) throws IOException {
        try (var input = loader.getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) throw new IOException("Missing class resource: " + name);
            return input.readAllBytes();
        }
    }

    private static String digest(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static Field accessible(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        if (!field.trySetAccessible()) throw new IllegalAccessException(type.getName() + "." + name);
        return field;
    }

    private static final class ReflectiveConfig implements PendingMixinPruner.ConfigView {
        private final Object config;
        private final Field name, pkg, prepared, common, client;
        ReflectiveConfig(Object config, Class<?> type) throws ReflectiveOperationException {
            this.config = config;
            name = accessible(type, "name"); pkg = accessible(type, "mixinPackage");
            prepared = accessible(type, "prepared"); common = accessible(type, "mixinClasses");
            client = accessible(type, "mixinClassesClient");
        }
        @Override public String name() throws IllegalAccessException { return (String) name.get(config); }
        @Override public String mixinPackage() throws IllegalAccessException { return (String) pkg.get(config); }
        @Override public boolean prepared() throws IllegalAccessException { return prepared.getBoolean(config); }
        @SuppressWarnings("unchecked")
        @Override public List<String> declarations(boolean sided) throws IllegalAccessException { return (List<String>) (sided ? client : common).get(config); }
        @Override public void declarations(boolean sided, List<String> values) throws IllegalAccessException { (sided ? client : common).set(config, values); }
    }

    private EarlyMixinPruner() { }
}
