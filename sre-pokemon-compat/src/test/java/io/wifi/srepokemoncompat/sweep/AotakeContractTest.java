package io.wifi.srepokemoncompat.sweep;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 对给定发行包只读检查真实签名，不加载其世界/配置及 Banira 依赖。 */
class AotakeContractTest {
    private static final String BASE = "io.wifi.srepokemoncompat.mixin.sweep.";

    @Test void configurationIsCommonOptionalAndStrict() throws Exception {
        try (var in = getClass().getResourceAsStream("/sre-pokemon-compat-aotake.mixins.json")) {
            assertNotNull(in);
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(AotakeMixinPlugin.class.getName(), json.get("plugin").getAsString());
            assertFalse(json.has("client"));
            assertEquals(3, json.getAsJsonArray("mixins").size());
            assertEquals(1, json.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        }
        var plugin = readClass(AotakeMixinPlugin.class.getName());
        boolean optional = false;
        for (var method : plugin.methods) for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("isModLoaded")) optional = true;
            if (insn instanceof MethodInsnNode call) assertFalse(call.owner.startsWith("net/minecraft/client/"));
        }
        assertTrue(optional);
    }

    @Test void exactAotakeJarContainsEveryStrictInjectionTarget() throws Exception {
        String path = System.getProperty("sre.aotake.jar"); assumeTrue(path != null, "Supply -PaotakeJar");
        try (var jar = new ZipFile(Path.of(path).toFile())) {
            var metadata = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("fabric.mod.json")).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("aotake_sweep", metadata.get("id").getAsString());
            assertEquals("2.0.0", metadata.get("version").getAsString());
            int checked = 0;
            for (String name : List.of("AotakeCandidateMixin", "AotakeRecyclingMixin", "AotakeRemovalMixin")) {
                var mixin = readClass(BASE + name);
                var target = annotation(mixin.invisibleAnnotations, "/Mixin;");
                assertNotNull(annotation(mixin.invisibleAnnotations, "/Pseudo;"));
                var upstream = readJar(jar, ((List<?>) value(target, "targets")).getFirst().toString());
                for (var method : mixin.methods) {
                    var inject = annotation(method.visibleAnnotations, "/Inject;");
                    if (inject == null) inject = annotation(method.invisibleAnnotations, "/Inject;");
                    if (inject == null) continue;
                    assertEquals(1, value(inject, "require"));
                    for (Object raw : (List<?>) value(inject, "method")) {
                        String selector = raw.toString(); int index = selector.indexOf('(');
                        String targetName = index < 0 ? selector : selector.substring(0, index);
                        String descriptor = index < 0 ? null : selector.substring(index);
                        var matches = upstream.methods.stream().filter(m -> m.name.equals(targetName)
                                && (descriptor == null || m.desc.equals(descriptor))).toList();
                        assertEquals(1, matches.size(), name + ":" + selector);
                        var actual = matches.getFirst();
                        assertEquals(method.access & Opcodes.ACC_STATIC, actual.access & Opcodes.ACC_STATIC);
                        var args = Type.getArgumentTypes(method.desc);
                        StringBuilder expected = new StringBuilder("(");
                        for (int i = 0; i < args.length - 1; i++) expected.append(production(args[i].getDescriptor()));
                        expected.append(")Z");
                        assertEquals(expected.toString(), actual.desc, selector);
                        assertTrue(method.instructions.iterator().hasNext());
                        boolean policy = false;
                        for (var insn : method.instructions) if (insn instanceof MethodInsnNode call
                                && call.owner.equals("io/wifi/srepokemoncompat/sweep/SweepProtection") && call.name.equals("isProtected")) policy = true;
                        assertTrue(policy);
                        checked++;
                    }
                }
            }
            assertEquals(4, checked);
            var committer = readJar(jar, "xin.vanilla.aotake.sweep.SweepBatchCommitter");
            assertTrue(committer.methods.stream().flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                    .anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("eligible") && call.desc.equals("(Ljava/lang/Object;)Z")));
        }
    }

    @Test void exactCobblemonJarProvidesBattleAndTypedPlayerOwnership() throws Exception {
        String path = System.getProperty("sre.cobblemon.jar"); assumeTrue(path != null, "Supply -PcobblemonJar");
        try (var jar = new ZipFile(Path.of(path).toFile())) {
            var entity = readJar(jar, "com.cobblemon.mod.common.entity.pokemon.PokemonEntity");
            assertTrue(entity.methods.stream().anyMatch(m -> m.name.equals("isBattling") && m.desc.equals("()Z")));
            assertTrue(entity.methods.stream().anyMatch(m -> m.name.equals("getPokemon")
                    && m.desc.equals("()Lcom/cobblemon/mod/common/pokemon/Pokemon;")));
            var pokemon = readJar(jar, "com.cobblemon.mod.common.pokemon.Pokemon");
            assertTrue(pokemon.methods.stream().anyMatch(m -> m.name.equals("isPlayerOwned") && m.desc.equals("()Z")));
        }
    }

    @Test void protectedPendingEntryCanCompleteWithoutRemovalAndDoesNotRetryForever() throws Exception {
        String path = System.getProperty("sre.aotake.jar"); assumeTrue(path != null, "Supply -PaotakeJar");
        byte[] bytes;
        try (var jar = new ZipFile(Path.of(path).toFile()); var in = jar.getInputStream(jar.getEntry("xin/vanilla/aotake/util/RemovalLedger.class"))) {
            bytes = in.readAllBytes();
        }
        var type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Object ledger = type.getConstructor().newInstance();
        Object key = new Object(), entity = new Object();
        assertEquals(true, type.getMethod("schedule", Object.class, Object.class, boolean.class).invoke(ledger, key, entity, false));
        var calls = new AtomicInteger();
        BiPredicate<Object, Boolean> protectedTask = (subject, instant) -> { assertSame(entity, subject); calls.incrementAndGet(); return true; };
        var flush = type.getMethod("flush", Object.class, BiPredicate.class);
        flush.invoke(ledger, key, protectedTask); flush.invoke(ledger, key, protectedTask);
        assertEquals(1, calls.get());
    }

    private static String production(String descriptor) {
        return descriptor.replace("Lnet/minecraft/world/entity/Entity;", "Lnet/minecraft/class_1297;")
                .replace("Lnet/minecraft/server/level/ServerLevel;", "Lnet/minecraft/class_3218;");
    }
    private static ClassNode readClass(String name) throws Exception {
        try (var in = AotakeContractTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(in, name); var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private static ClassNode readJar(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name.replace('.', '/') + ".class"); assertNotNull(entry, name);
        try (var in = jar.getInputStream(entry)) { var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node; }
    }
    private static AnnotationNode annotation(List<AnnotationNode> annotations, String suffix) {
        return annotations == null ? null : annotations.stream().filter(a -> a.desc.endsWith(suffix)).findFirst().orElse(null);
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
}
