package io.wifi.srepackcompat.server;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 将真实补丁的方法体接到轻量实体桩，避免为边界测试启动整个 Minecraft。 */
public class TrackedEntityRangeTest {
    public static class EntityStub {
        public List<?> passengers = List.of();
        public List<?> getPassengers() { return passengers; }
    }

    private record Probe(Object instance, EntityStub entity, Class<?> type) {
        CallbackInfoReturnable<Integer> run() throws Exception {
            var callback = new CallbackInfoReturnable<Integer>("getEffectiveRange", true);
            var method = type.getDeclaredMethod("sre$emptyPassengerRange", CallbackInfoReturnable.class);
            method.setAccessible(true);
            method.invoke(instance, callback);
            return callback;
        }
    }

    private Probe probe(int range) throws Exception {
        String original = "io/wifi/srepackcompat/mixin/server/TrackedEntityRangeMixin";
        String renamed = "io/wifi/srepackcompat/server/TrackingRangeProbe";
        var node = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(original + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassRemapper(node, new SimpleRemapper(Map.of(
                    original, renamed, "net/minecraft/world/entity/Entity", Type.getInternalName(EntityStub.class)))), 0);
        }
        node.access &= ~Opcodes.ACC_ABSTRACT;
        // 唯一桩方法模拟服务端距离倍率；被测 handler 和空乘客分支保持编译后的原始字节码。
        var scale = node.methods.stream().filter(m -> m.name.equals("scaledRange")).findFirst().orElseThrow();
        scale.access &= ~Opcodes.ACC_ABSTRACT;
        scale.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        scale.instructions.add(new InsnNode(Opcodes.ICONST_2));
        scale.instructions.add(new InsnNode(Opcodes.IMUL));
        scale.instructions.add(new InsnNode(Opcodes.IRETURN));
        for (var field : node.fields) field.access &= ~Opcodes.ACC_FINAL;
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        class Loader extends ClassLoader {
            Loader() { super(TrackedEntityRangeTest.class.getClassLoader()); }
            Class<?> loadProbe() { byte[] bytes = writer.toByteArray(); return defineClass(null, bytes, 0, bytes.length); }
        }
        var type = new Loader().loadProbe();
        var instance = type.getConstructor().newInstance();
        var entity = new EntityStub();
        var entityField = type.getDeclaredField("entity"); entityField.setAccessible(true); entityField.set(instance, entity);
        var rangeField = type.getDeclaredField("range"); rangeField.setAccessible(true); rangeField.setInt(instance, range);
        return new Probe(instance, entity, type);
    }

    @Test void emptyPassengersUseCurrentScaledRange() throws Exception {
        var callback = probe(80).run();
        assertTrue(callback.isCancelled());
        assertEquals(160, callback.getReturnValueI());
    }

    @Test void mountedEntitiesRetainOriginalCalculation() throws Exception {
        var probe = probe(80);
        probe.entity.passengers = List.of(new Object());
        assertFalse(probe.run().isCancelled());
    }

    @Test void mountingAndDismountingAreVisibleImmediately() throws Exception {
        var probe = probe(64);
        assertEquals(128, probe.run().getReturnValueI());
        probe.entity.passengers = List.of(new Object());
        assertFalse(probe.run().isCancelled());
        probe.entity.passengers = List.of();
        assertEquals(128, probe.run().getReturnValueI());
    }

    @Test void zeroRangeIsNotPromotedToDefaultDistance() throws Exception {
        assertEquals(0, probe(0).run().getReturnValueI());
    }
}
