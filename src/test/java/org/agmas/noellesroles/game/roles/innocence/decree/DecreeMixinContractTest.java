package org.agmas.noellesroles.game.roles.innocence.decree;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 读取真实依赖字节码核对全部律令注入目标，不初始化 Minecraft 注册表。 */
class DecreeMixinContractTest {
    private ClassNode readClass(String name) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) all.addAll(visible);
        if (invisible != null) all.addAll(invisible);
        return all;
    }
    private Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }
    @Test void everyRegisteredDecreeInjectionTargetsAnExistingMethod() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/noellesroles.mixins.json")) {
            assertNotNull(stream);
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            int checked = 0;
            for (String section : List.of("mixins", "client")) {
                for (var entry : config.getAsJsonArray(section)) {
                    String shortName = entry.getAsString();
                    if (!shortName.contains(".decree.")) continue;
                    var mixin = readClass(config.get("package").getAsString() + "." + shortName);
                    var annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                    List<String> targets = new ArrayList<>();
                    if (value(annotation, "value") instanceof List<?> types) {
                        for (Object type : types) targets.add(((Type) type).getClassName());
                    }
                    if (value(annotation, "targets") instanceof List<?> names) {
                        for (Object name : names) targets.add(name.toString());
                    }
                    assertFalse(targets.isEmpty(), shortName);
                    for (String target : targets) {
                        ClassNode targetClass = readClass(target);
                        for (var method : mixin.methods) {
                            for (var injector : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                                if (!(value(injector, "method") instanceof List<?> selectors)) continue;
                                for (Object selector : selectors) {
                                    String text = selector.toString();
                                    int descriptor = text.indexOf('(');
                                    String name = descriptor < 0 ? text : text.substring(0, descriptor);
                                    String desc = descriptor < 0 ? null : text.substring(descriptor);
                                    assertTrue(targetClass.methods.stream().anyMatch(m -> m.name.equals(name)
                                                    && (desc == null || m.desc.equals(desc))),
                                            shortName + " -> " + target + "." + text);
                                    checked++;
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(checked >= 20, "应检查客户端与服务端全部律令注入方法");
        }
    }
}
