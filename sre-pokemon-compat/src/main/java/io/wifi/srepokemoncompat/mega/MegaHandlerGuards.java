package io.wifi.srepokemoncompat.mega;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.HashSet;
import java.util.Set;

/** 只给已合并的指定 Mega 注入处理器加开关，绝不向其原版宿主方法插入 return。 */
final class MegaHandlerGuards {
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";

    static void apply(ClassNode target, String source, String originalName, Set<String> descriptors, String authority) {
        var found = new HashSet<String>();
        var handlers = new java.util.ArrayList<org.objectweb.asm.tree.MethodNode>();
        for (var method : target.methods) {
            if (!method.name.equals(originalName) && !method.name.endsWith("$" + originalName)) continue;
            if (method.visibleAnnotations == null || method.visibleAnnotations.stream().noneMatch(a -> from(a, source))) continue;
            if (!descriptors.contains(method.desc) || !found.add(method.desc)) {
                throw new IllegalStateException("Unexpected Mega handler signature: " + method.name + method.desc);
            }
            handlers.add(method);
        }
        if (!found.equals(descriptors)) {
            throw new IllegalStateException("Missing Mega handlers in " + target.name + ": " + source + "." + originalName);
        }
        // 先验证全部签名再修改；不受两个 useOnEntity 重载的排列顺序影响。
        for (var method : handlers) {
            var proceed = new LabelNode();
            var guard = new InsnList();
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, authority, "enabled", "()Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFNE, proceed));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(proceed);
            guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            method.instructions.insert(guard);
            method.maxStack = Math.max(1, method.maxStack);
        }
    }

    private static boolean from(AnnotationNode annotation, String source) {
        if (!MERGED.equals(annotation.desc) || annotation.values == null) return false;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if ("mixin".equals(annotation.values.get(i))) return source.equals(annotation.values.get(i + 1));
        }
        return false;
    }

    private MegaHandlerGuards() { }
}
