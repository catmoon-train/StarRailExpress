package io.wifi.srepackcompat.startup;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class EarlyMixinPrunerTest {
    @TempDir Path directory;

    @Test
    void missingSwitchDefaultsOffWithoutCreatingFile() throws Exception {
        Path path = directory.resolve("absent/mixin.properties");
        assertFalse(EarlyMixinPruner.enabled(path));
        assertFalse(Files.exists(path.getParent()));
    }

    @Test
    void explicitSwitchIsPreservedAndMalformedOptionsFailClosed() throws Exception {
        Path path = directory.resolve("mixin.properties");
        Files.writeString(path, "# 测试\nenabled= FALSE \n", StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(path);
        assertFalse(EarlyMixinPruner.enabled(path));
        assertArrayEquals(original, Files.readAllBytes(path));
        Files.writeString(path, "enabled=true", StandardCharsets.UTF_8);
        assertTrue(EarlyMixinPruner.enabled(path));
        Files.writeString(path, "# no explicit opt-in", StandardCharsets.UTF_8);
        assertFalse(EarlyMixinPruner.enabled(path));
        Files.writeString(path, "enabled=maybe", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> EarlyMixinPruner.enabled(path));
        assertThrows(IOException.class, () -> EarlyMixinPruner.enabled(directory));
    }

    @Test
    void requiresExactRegisteredJavaCancellerNotSimilarEntry() {
        var metadata = JsonParser.parseString("""
                {"entrypoints":{"mixinsquared":["io.wifi.WatheMixinBlocker", "io.wifi.Other",
                  {"adapter":"custom", "value":"io.wifi.SodiumExtraLightOptionMixinBlocker"}]}}
                """).getAsJsonObject();
        assertTrue(EarlyMixinPruner.registered(metadata, "io.wifi.WatheMixinBlocker"));
        assertFalse(EarlyMixinPruner.registered(metadata, "io.wifi.WatheMixinBlocker2"));
        assertFalse(EarlyMixinPruner.registered(metadata, "io.wifi.SodiumExtraLightOptionMixinBlocker"));
        assertFalse(EarlyMixinPruner.registered(JsonParser.parseString("{}").getAsJsonObject(), "io.wifi.WatheMixinBlocker"));
    }

    @Test
    void exactKnownBytesAreRequiredBeforeParsing() throws Exception {
        byte[] bytes = mixin(Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> { });
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertTrue(EarlyMixinPruner.verifiedMixin(bytes, hash));
        assertFalse(EarlyMixinPruner.verifiedMixin(bytes, "0".repeat(64)));
        assertFalse(EarlyMixinPruner.verifiedMixin(new byte[]{1, 2, 3}, hash));
    }

    @Test
    void interfacesInheritanceAndImplementedApisRetainOriginalPreparation() {
        assertFalse(EarlyMixinPruner.simpleMixin(mixin(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                "java/lang/Object", null, cw -> { })));
        assertFalse(EarlyMixinPruner.simpleMixin(mixin(Opcodes.ACC_PUBLIC, "other/BaseMixin", null, cw -> { })));
        assertFalse(EarlyMixinPruner.simpleMixin(mixin(Opcodes.ACC_PUBLIC, "java/lang/Object", new String[]{"api/Access"}, cw -> { })));
    }

    @Test
    void accessorAndInvokerAnnotationsAreNotPrunedEvenOnClasses() {
        for (String annotation : new String[]{"Accessor", "Invoker"}) {
            byte[] bytes = mixin(Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> {
                var method = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "access", "()V", null, null);
                method.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/" + annotation + ";", true).visitEnd();
                method.visitEnd();
            });
            assertFalse(EarlyMixinPruner.simpleMixin(bytes), annotation);
        }
    }

    @Test
    void innerOuterAndNestMetadataRemainUntouched() {
        for (Consumer<ClassWriter> metadata : java.util.List.<Consumer<ClassWriter>>of(
                cw -> cw.visitInnerClass("test/Mixin$Inner", "test/Mixin", "Inner", Opcodes.ACC_STATIC),
                cw -> cw.visitOuterClass("test/Outer", null, null),
                cw -> cw.visitNestHost("test/Host"),
                cw -> cw.visitNestMember("test/Mixin$Member"))) {
            assertFalse(EarlyMixinPruner.simpleMixin(mixin(Opcodes.ACC_PUBLIC, "java/lang/Object", null, metadata)));
        }
    }

    @Test
    void bundledManifestOnlyContainsKnownCancelledNamesAndValidDigests() throws Exception {
        Properties known = new Properties();
        try (var stream = getClass().getResourceAsStream("/sre-pack-compat-early-mixins.properties")) {
            assertNotNull(stream);
            known.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        assertEquals(42, known.size());
        var policy = new SreMixinCancellationPolicy(true, true);
        for (String name : known.stringPropertyNames()) {
            assertTrue(policy.cancels(name), name);
            assertTrue(known.getProperty(name).matches("[0-9a-f]{64}"), name);
            assertFalse(name.contains("Accessor"), name);
        }
        assertFalse(known.containsKey("dev.doctor4t.wathe.mixin.EntityMixin"));
        assertFalse(known.containsKey("dev.doctor4t.ratatouille.mixin.client.BlockRenderManagerAccessor"));
    }

    private static byte[] mixin(int access, String parent, String[] interfaces, Consumer<ClassWriter> metadata) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, access, "test/Mixin", null, parent, interfaces);
        cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitEnd();
        metadata.accept(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }
}
