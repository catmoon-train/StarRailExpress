package io.wifi.starrailexpress.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** 核对真实编译补丁的作用层和非阻塞约束，不启动 Minecraft。 */
class LocalPlayerSkinMixinTest {
    private ClassNode read(String name) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "io/wifi/starrailexpress/mixin/client/" + name + ".class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    @Test void recoveryDoesNotBlockForLoginProfile() throws IOException {
        var node = read("LocalPlayerSkinMixin");
        boolean nonBlockingRead = false;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(call.owner.equals("java/util/concurrent/CompletableFuture")
                            && (call.name.equals("join") || call.name.equals("get")));
                    assertFalse(call.owner.equals("net/minecraft/client/Minecraft") && call.name.equals("getGameProfile"));
                    nonBlockingRead |= call.owner.equals("java/util/concurrent/CompletableFuture") && call.name.equals("getNow");
                }
            }
        }
        assertTrue(nonBlockingRead);
    }

    @Test void localIdentityAndStreamingModeAreCheckedBeforeFallback() throws IOException {
        var method = read("LocalPlayerSkinMixin").methods.stream()
                .filter(m -> m.name.equals("sre$restoreOwnSkin")).findFirst().orElseThrow();
        int uuidCheck = -1;
        int streamingCheck = -1;
        int resolve = -1;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("java/util/UUID") && call.name.equals("equals")) uuidCheck = method.instructions.indexOf(call);
                if (call.name.equals("resolve")) resolve = method.instructions.indexOf(call);
            }
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                    && field.name.equals("enableRandomSkinForStreaming")) streamingCheck = method.instructions.indexOf(field);
        }
        assertTrue(uuidCheck >= 0 && uuidCheck < resolve);
        assertTrue(streamingCheck >= 0 && streamingCheck < resolve);
    }
}
