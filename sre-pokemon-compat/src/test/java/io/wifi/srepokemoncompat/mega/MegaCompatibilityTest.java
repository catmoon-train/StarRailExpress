package io.wifi.srepokemoncompat.mega;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class MegaCompatibilityTest {
    private static final String PACKAGE = "io.wifi.srepokemoncompat.mixin.mega.";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String AUTHORITY = Type.getInternalName(GuardAuthority.class);

    @Test void optionalModAndEnvironmentSelection() throws Exception {
        for (var name : mixins()) for (boolean client : List.of(false, true)) {
            assertFalse(MegaMixinPlugin.applies(false, client, name));
            assertEquals(client || !name.startsWith(PACKAGE + "client."), MegaMixinPlugin.applies(true, client, name));
        }
        for (var method : readClass(MegaMixinPlugin.class.getName()).methods) for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call) {
                assertFalse(call.owner.equals("java/lang/Class") && call.name.equals("forName"));
                assertFalse(call.name.equals("loadClass"));
            }
        }
    }

    @Test void strictOptionalMixinsUseCorrectAuthorityAndPreserveCleanup() throws Exception {
        var forbidden = Set.of("register", "init", "sync", "onDestroyed", "dynamaxEnded", "devolveFainted",
                "hookBattleStarted", "fixTera", "revert", "startGradualScalingDown");
        for (var name : mixins()) {
            var node = readClass(name);
            assertNotNull(annotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Pseudo;"), name);
            for (var method : node.methods) {
                var inject = injectAnnotation(method);
                if (inject != null) {
                    assertTrue((Integer) value(inject, "require") >= 1, name);
                    for (var selector : selectors(inject)) assertFalse(forbidden.contains(selector), selector);
                    String expected = name.startsWith(PACKAGE + "client.")
                            ? "io/wifi/srepackcompat/cobblemon/CobblemonPolicy"
                            : "io/wifi/starrailexpress/compat/cobblemon/CobblemonMechanics";
                    boolean gate = false, cancel = false;
                    for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) {
                        if (call.owner.equals(expected) && call.name.equals("enabled")) gate = true;
                        if (call.name.equals("cancel")) cancel = true;
                    }
                    assertTrue(gate && cancel, name + "." + method.name);
                }
                assertFalse(method.desc.contains("com/github/yajatkaul/mega_showdown/"));
                for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) {
                    assertFalse(call.owner.startsWith("com/github/yajatkaul/mega_showdown/"), name);
                    assertFalse(call.owner.startsWith("com/cobblemon/"), name);
                    assertFalse(call.owner.endsWith("/SREConfig"), name);
                    if (!name.startsWith(PACKAGE + "client.")) assertFalse(call.owner.startsWith("net/minecraft/client/"));
                }
            }
        }
    }

    @Test void mergedHandlerGuardsVerifyFramesAndPreserveHostAndOtherMods() throws Exception {
        var node = new ClassNode();
        node.version = Opcodes.V21; node.access = Opcodes.ACC_PUBLIC;
        node.name = "io/wifi/srepokemoncompat/mega/GeneratedMegaGuard"; node.superName = "java/lang/Object";
        node.methods.add(handler("handler$mega$useOnEntity", "()V", MegaMixinPlugin.ITEM_MIXIN));
        node.methods.add(handler("handler$mega$useOnEntity", "(I)V", MegaMixinPlugin.ITEM_MIXIN));
        node.methods.add(handler("handler$mega$onDestroyed", "()V", MegaMixinPlugin.ITEM_MIXIN));
        node.methods.add(handler("handler$other$useOnEntity", "()V", "other.ModMixin"));
        node.methods.add(handler("hostUse", "()V", null));
        MegaHandlerGuards.apply(node, MegaMixinPlugin.ITEM_MIXIN, "useOnEntity", Set.of("()V", "(I)V"), AUTHORITY);
        // 不计算帧，验证生产插入的 F_SAME 帧本身有效。
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        var type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        }.define(writer.toByteArray());
        GuardAuthority.enabled = false; GuardAuthority.calls = 0;
        type.getMethod("handler$mega$useOnEntity").invoke(null);
        type.getMethod("handler$mega$useOnEntity", int.class).invoke(null, 1);
        assertEquals(0, GuardAuthority.calls);
        type.getMethod("hostUse").invoke(null);
        type.getMethod("handler$other$useOnEntity").invoke(null);
        type.getMethod("handler$mega$onDestroyed").invoke(null);
        assertEquals(3, GuardAuthority.calls);
        GuardAuthority.enabled = true;
        type.getMethod("handler$mega$useOnEntity").invoke(null);
        type.getMethod("handler$mega$useOnEntity", int.class).invoke(null, 1);
        assertEquals(5, GuardAuthority.calls);
    }

    @Test void missingHandlerIsAnErrorNotSilentPartialProtection() {
        var node = new ClassNode();
        var method = handler("handler$mega$useOnEntity", "()V", MegaMixinPlugin.ITEM_MIXIN);
        node.methods.add(method);
        assertThrows(IllegalStateException.class, () -> MegaHandlerGuards.apply(node, MegaMixinPlugin.ITEM_MIXIN,
                "useOnEntity", Set.of("()V", "(I)V"), AUTHORITY));
        assertEquals("touch", ((MethodInsnNode) method.instructions.getFirst()).name);
    }

    /** 可选发行 JAR 校验只读 ASM 字节码，不加载目标及其依赖。 */
    @Test void targetJarMatchesEveryInjectionAndMergedHandlerSignature() throws Exception {
        String path = System.getProperty("sre.mega.jar");
        assumeTrue(path != null && !path.isBlank(), "设置 -Dsre.mega.jar=<发行 JAR> 才校验外部字节码");
        try (var jar = new ZipFile(Path.of(path).toFile())) {
            for (var name : mixins()) {
                var mixin = readClass(name);
                if (name.endsWith("MegaItemStackHandlerMixin") || name.endsWith("MegaClientDelegateHandlerMixin")) {
                    boolean item = name.endsWith("MegaItemStackHandlerMixin");
                    String source = item ? MegaMixinPlugin.ITEM_MIXIN : MegaMixinPlugin.DELEGATE_MIXIN;
                    String original = item ? "useOnEntity" : "tick";
                    var upstream = readJar(jar, source);
                    for (var method : upstream.methods) if (method.name.equals(original)) {
                        method.visibleAnnotations = new ArrayList<>(List.of(merged(source)));
                        method.name = "handler$mega$" + original;
                    }
                    MegaHandlerGuards.apply(upstream, source, original,
                            item ? MegaMixinPlugin.ITEM_HANDLERS : Set.of(MegaMixinPlugin.DELEGATE_HANDLER), AUTHORITY);
                    continue;
                }
                var target = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
                @SuppressWarnings("unchecked") var targets = (List<String>) value(target, "targets");
                assertNotNull(targets, name);
                for (var method : mixin.methods) {
                    var inject = injectAnnotation(method);
                    if (inject == null) continue;
                    var names = selectors(inject);
                    for (var targetName : targets) {
                        var upstream = readJar(jar, targetName);
                        var matches = upstream.methods.stream().filter(m -> names.contains(m.name)).toList();
                        assertEquals((Integer) value(inject, "require"), matches.size(), name + " -> " + targetName);
                        for (var match : matches) {
                            assertEquals(method.access & Opcodes.ACC_STATIC, match.access & Opcodes.ACC_STATIC, name);
                            assertEquals(Type.VOID_TYPE, Type.getReturnType(match.desc), name);
                        }
                    }
                }
            }
        }
    }

    public static final class GuardAuthority {
        static boolean enabled;
        static int calls;
        public static boolean enabled() { return enabled; }
        public static void touch() { calls++; }
    }

    private static MethodNode handler(String name, String descriptor, String source) {
        var method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
        if (source != null) method.visibleAnnotations = new ArrayList<>(List.of(merged(source)));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, AUTHORITY, "touch", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private static AnnotationNode merged(String source) {
        var annotation = new AnnotationNode(MERGED);
        annotation.values = new ArrayList<>(List.of("mixin", source));
        return annotation;
    }

    private static AnnotationNode injectAnnotation(MethodNode method) {
        var annotation = annotation(method.visibleAnnotations, INJECT);
        return annotation != null ? annotation : annotation(method.invisibleAnnotations, INJECT);
    }

    private static List<String> mixins() throws Exception {
        try (var in = MegaCompatibilityTest.class.getClassLoader().getResourceAsStream("sre-pokemon-compat-mega.mixins.json")) {
            assertNotNull(in);
            var config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            assertEquals(MegaMixinPlugin.class.getName(), config.get("plugin").getAsString());
            var result = new ArrayList<String>();
            for (var section : List.of("mixins", "client")) for (var entry : config.getAsJsonArray(section)) {
                String name = entry.getAsString();
                assertEquals(section.equals("client"), name.startsWith("client."));
                result.add(PACKAGE + name);
            }
            return result;
        }
    }

    private static ClassNode readClass(String name) throws Exception {
        try (var in = MegaCompatibilityTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(in, name);
            var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }

    private static ClassNode readJar(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name.replace('.', '/') + ".class"); assertNotNull(entry, name);
        try (var in = jar.getInputStream(entry)) {
            var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream().filter(a -> a.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation != null && annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> selectors(AnnotationNode annotation) { return (List<String>) value(annotation, "method"); }
}
