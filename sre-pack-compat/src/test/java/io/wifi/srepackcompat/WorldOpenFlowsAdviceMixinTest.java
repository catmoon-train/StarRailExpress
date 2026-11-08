package io.wifi.srepackcompat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/** 直接核对 1.21.1 映射字节码，不初始化客户端、注册表或世界。 */
class WorldOpenFlowsAdviceMixinTest {
    private static final String LOAD = "(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/WorldStem;Lnet/minecraft/server/packs/repository/PackRepository;Ljava/lang/Runnable;)V";
    private static final String CREATE = "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;Lcom/mojang/serialization/Lifecycle;Ljava/lang/Runnable;Z)V";

    @Test
    void loadingRedirectMatchesExactlyOneLifecycleQueryAndKeepsLegacyBackupCheck() throws Exception {
        var method = method(read("net/minecraft/client/gui/screens/worldselection/WorldOpenFlows"),
                "openWorldCheckWorldStemCompatibility", LOAD);
        assertEquals(1, StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(node -> node instanceof MethodInsnNode call
                        && call.owner.equals("net/minecraft/world/level/storage/WorldData")
                        && call.name.equals("worldGenSettingsLifecycle")
                        && call.desc.equals("()Lcom/mojang/serialization/Lifecycle;")).count());
        assertTrue(StreamSupport.stream(method.instructions.spliterator(), false)
                .anyMatch(node -> node instanceof MethodInsnNode call && call.name.equals("isOldCustomizedWorld")));
    }

    @Test
    void creationBypassIsTheSingleBooleanArgumentInStaticSlotFour() throws Exception {
        var method = method(read("net/minecraft/client/gui/screens/worldselection/WorldOpenFlows"),
                "confirmWorldCreation", CREATE);
        assertTrue((method.access & Opcodes.ACC_STATIC) != 0);
        Type[] args = Type.getArgumentTypes(method.desc);
        assertEquals(5, args.length);
        assertEquals(Type.BOOLEAN_TYPE, args[4]);
        assertEquals(4, Arrays.stream(args).limit(4).mapToInt(Type::getSize).sum());
        assertTrue(StreamSupport.stream(method.instructions.spliterator(), false)
                .anyMatch(node -> node instanceof VarInsnNode load && load.getOpcode() == Opcodes.ILOAD && load.var == 4));
    }

    @Test
    void patchIsRegisteredOnlyOnClientAndBothHandlersKeepRequestedBehavior() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/sre-pack-compat.mixins.json")) {
            assertNotNull(input);
            var config = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String name = "client.WorldOpenFlowsAdviceMixin";
            assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(entry -> name.equals(entry.getAsString())));
            assertFalse(config.has("mixins") && config.getAsJsonArray("mixins").asList().stream()
                    .anyMatch(entry -> name.equals(entry.getAsString())));
        }
        var patch = read("io/wifi/starrailexpress/mixin/client/WorldOpenFlowsAdviceMixin");
        var load = method(patch, "srepackcompat$stableLifecycleForAdvice", "(Lnet/minecraft/world/level/storage/WorldData;)Lcom/mojang/serialization/Lifecycle;");
        assertTrue(StreamSupport.stream(load.instructions.spliterator(), false).anyMatch(node ->
                node instanceof MethodInsnNode call && call.owner.equals("com/mojang/serialization/Lifecycle")
                        && call.name.equals("stable")));
        var creation = method(patch, "srepackcompat$bypassCreationAdvice", "(Z)Z");
        assertTrue(StreamSupport.stream(creation.instructions.spliterator(), false)
                .anyMatch(node -> node.getOpcode() == Opcodes.ICONST_1));
        assertTrue((creation.access & Opcodes.ACC_STATIC) != 0);
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = WorldOpenFlowsAdviceMixinTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError("缺少注入目标: " + name + descriptor));
    }
}
