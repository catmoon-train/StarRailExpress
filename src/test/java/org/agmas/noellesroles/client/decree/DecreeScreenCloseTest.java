package org.agmas.noellesroles.client.decree;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class DecreeScreenCloseTest {
    @Test void escapeCloseReturnsDirectlyToGameWithoutPauseScreen() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "org/agmas/noellesroles/client/screen/DecreeScreen.class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var close = node.methods.stream().filter(m -> m.name.equals("onClose") && m.desc.equals("()V"))
                    .findFirst().orElseThrow();
            boolean closesToNull = false;
            for (var instruction : close.instructions) {
                assertFalse(instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW,
                        "关闭律令页不应创建暂停页面");
                if (instruction instanceof MethodInsnNode call && call.name.equals("setScreen")) {
                    assertEquals(Opcodes.ACONST_NULL, call.getPrevious().getOpcode());
                    closesToNull = true;
                }
            }
            assertTrue(closesToNull, "ESC 应直接 setScreen(null)");
        }
    }
}
