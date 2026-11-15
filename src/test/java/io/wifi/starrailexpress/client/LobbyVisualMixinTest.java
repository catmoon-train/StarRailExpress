package io.wifi.starrailexpress.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** 检查编译后的大厅短路路径，不在无游戏环境中加载 SRE 或调用 OpenGL。 */
class LobbyVisualMixinTest {
    private MethodNode method(String name, String method) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "io/wifi/starrailexpress/mixin/client/" + name + ".class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node.methods.stream().filter(m -> m.name.equals(method)).findFirst().orElseThrow();
        }
    }

    private AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    @Test void lobbyAlwaysRestoresVanillaFogBeforeAnyCustomFog() throws IOException {
        var method = method("scenery/WorldRendererMixin", "tmm$applyBlizzardFog");
        var check = assertInstanceOf(FieldInsnNode.class, next(method.instructions.getFirst()));
        assertEquals("io/wifi/starrailexpress/SRE", check.owner);
        assertEquals("isLobby", check.name);
        var branch = assertInstanceOf(JumpInsnNode.class, next(check));
        assertEquals(Opcodes.IFEQ, branch.getOpcode());
        int calls = 0;
        int resetFlags = 0;
        boolean clearShaderFog = false;
        boolean returns = false;
        for (var instruction = next(branch); instruction != branch.label; instruction = instruction.getNext()) {
            assertNotNull(instruction);
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")) calls++;
                else if (!call.owner.startsWith("java/lang/")) {
                    assertEquals("io/wifi/starrailexpress/client/render/ShaderPackFog", call.owner);
                    assertEquals("beginFrame", call.name);
                    clearShaderFog = true;
                }
            }
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC) {
                assertTrue(field.name.equals("tmm$echoFogApplied") || field.name.equals("tmm$nightFogApplied"));
                assertEquals(Opcodes.ICONST_0, field.getPrevious().getOpcode());
                resetFlags++;
            }
            if (instruction.getOpcode() == Opcodes.RETURN) returns = true;
        }
        assertEquals(1, calls, "大厅每次调用原版雾设置，不能只在首次切换时恢复");
        assertEquals(2, resetFlags);
        assertTrue(clearShaderFog);
        assertTrue(returns);
        assertTrue(method.instructions.indexOf(branch.label) > method.instructions.indexOf(branch));
    }

    @Test void creativeNoFogCannotOverrideLobbyVanillaRange() throws IOException {
        var method = method("scenery/CreativeNoFogMixin", "onSetupFog");
        var check = assertInstanceOf(FieldInsnNode.class, next(method.instructions.getFirst()));
        assertEquals("io/wifi/starrailexpress/SRE", check.owner);
        assertEquals("isLobby", check.name);
        var branch = assertInstanceOf(JumpInsnNode.class, next(check));
        assertEquals(Opcodes.IFEQ, branch.getOpcode());
        assertEquals(Opcodes.RETURN, next(branch).getOpcode());
    }

    @Test void nativeOptionReadsAndWritesUseCurrentBrightnessPolicy() throws IOException {
        for (String name : new String[]{"sre$rejectLockedValueChanges", "sre$enforceLockedRead"}) {
            var method = method("restrictions/LockedOptionInstanceMixin", name);
            boolean dynamicLookup = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("sre$currentLockedValue")) dynamicLookup = true;
            }
            assertTrue(dynamicLookup, name);
        }
    }
}
