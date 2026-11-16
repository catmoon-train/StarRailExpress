package io.wifi.srepokemoncompat.journeymap;

import com.google.gson.JsonParser;
import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JourneyMapCompatibilityTest {
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

    @AfterEach void reset() throws Exception { enabled(false); }

    @Test void guardsActuallyCancelOnlyWithoutServerPermission() throws Exception {
        int count = 0;
        for (String name : mixins()) {
            var node = read(name);
            assertNotNull(annotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Pseudo;"));
            for (var method : node.methods) {
                var inject = annotation(method.visibleAnnotations, INJECT);
                if (inject == null) continue;
                assertEquals(Boolean.TRUE, value(inject, "cancellable"));
                assertTrue((Integer) value(inject, "require") >= 1);
                assertTrue(method.instructions.iterator().hasNext());
                // 单独执行原处理器字节码，不启动 Minecraft、不加载目标模组。
                String callbackName = Type.getArgumentTypes(method.desc)[0].getClassName();
                Class<?> callbackType = Class.forName(callbackName);
                var harness = new ClassNode();
                harness.version = Opcodes.V21;
                harness.access = Opcodes.ACC_PUBLIC;
                harness.name = "test/JourneyMapGuard" + count++;
                harness.superName = "java/lang/Object";
                method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                method.desc = "(Ljava/lang/Object;" + Type.getDescriptor(callbackType) + ")V";
                harness.methods.add(method);
                var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                harness.accept(writer);
                Class<?> type = new ClassLoader(getClass().getClassLoader()) {
                    Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
                }.define(writer.toByteArray());
                for (boolean state : List.of(false, true, false)) {
                    enabled(state);
                    CallbackInfo callback = callbackType == CallbackInfo.class
                            ? new CallbackInfo("test", true) : new CallbackInfoReturnable<>("test", true);
                    type.getMethod(method.name, Object.class, callbackType).invoke(null, null, callback);
                    assertEquals(!state, callback.isCancelled(), name + " enabled=" + state);
                }
            }
        }
        assertEquals(11, count);
    }

    @Test void targetJarMatchesAllGuardsAndPreservesCleanup() throws Exception {
        String path = System.getProperty("sre.journeymap.jar");
        assumeTrue(path != null && !path.isBlank(), "提供 -PjourneymapJar=<发行 JAR> 以校验目标");
        var cleanup = Set.of("stopMapping", "stopWebMap", "onDisconnect", "onUnload", "onWorldUnload",
                "onChunkDrop", "reset", "clear", "saveConfigProperties", "onScreenClosedEvent");
        try (var jar = new ZipFile(Path.of(path).toFile())) {
            for (String name : mixins()) {
                var mixin = read(name);
                var target = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
                assertEquals(Boolean.FALSE, value(target, "remap"));
                @SuppressWarnings("unchecked") var targets = (List<String>) value(target, "targets");
                assertEquals(1, targets.size());
                var entry = jar.getEntry(targets.getFirst().replace('.', '/') + ".class");
                assertNotNull(entry, targets.getFirst());
                var upstream = new ClassNode();
                try (var in = jar.getInputStream(entry)) { new ClassReader(in).accept(upstream, 0); }
                for (var method : mixin.methods) {
                    if (annotation(method.visibleAnnotations, SHADOW) != null) {
                        assertTrue(upstream.methods.stream().anyMatch(m -> m.name.equals(method.name)
                                && m.desc.equals(method.desc)), "shadow " + method.name);
                    }
                    var inject = annotation(method.visibleAnnotations, INJECT);
                    if (inject == null) continue;
                    @SuppressWarnings("unchecked") var selectors = (List<String>) value(inject, "method");
                    for (String selector : selectors) {
                        assertFalse(cleanup.contains(selector), "不得拦截清理入口 " + selector);
                        assertTrue(upstream.methods.stream().anyMatch(m -> m.name.equals(selector)), selector);
                    }
                    var matched = upstream.methods.stream().filter(m -> selectors.contains(m.name)).toList();
                    assertEquals((Integer) value(inject, "require"), matched.size(), name);
                    boolean returnsValue = method.desc.contains("CallbackInfoReturnable;");
                    for (var match : matched) {
                        assertEquals(returnsValue, Type.getReturnType(match.desc).getSort() != Type.VOID, match.name);
                        assertTrue((match.access & Opcodes.ACC_STATIC) == 0, match.name);
                    }
                }
            }
            // 目标所有已注册键位都必须纳入共享过滤，不误伤原版键位。
            var keys = new ClassNode();
            try (var in = jar.getInputStream(jar.getEntry("journeymap/client/event/handlers/KeybindingHandler.class"))) {
                new ClassReader(in).accept(keys, 0);
            }
            int keysFound = 0;
            for (var method : keys.methods) for (var insn : method.instructions) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String text && text.startsWith("key.")) {
                    assertTrue(CobblemonPolicy.isKey(text), text);
                    keysFound++;
                }
            }
            assertTrue(keysFound >= 20);
        }
    }

    private static void enabled(boolean state) throws Exception {
        var setter = CobblemonPolicy.class.getDeclaredMethod("setEnabled", boolean.class);
        setter.setAccessible(true);
        setter.invoke(null, state);
    }

    private static List<String> mixins() throws Exception {
        try (var in = JourneyMapCompatibilityTest.class.getResourceAsStream("/sre-pokemon-compat-journeymap.mixins.json")) {
            assertNotNull(in);
            var config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(config.has("mixins"));
            return config.getAsJsonArray("client").asList().stream()
                    .map(v -> config.get("package").getAsString() + "." + v.getAsString()).toList();
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (var in = JourneyMapCompatibilityTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(in, name);
            var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }

    private static AnnotationNode annotation(List<AnnotationNode> list, String desc) {
        return list == null ? null : list.stream().filter(a -> a.desc.equals(desc)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode node, String key) {
        if (node != null && node.values != null) for (int i = 0; i < node.values.size(); i += 2) {
            if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        }
        return null;
    }
}
