package org.agmas.noellesroles.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 隔离执行实际载具掉落回调，不初始化游戏注册表。 */
class VehicleEntityNoDropMixinTest {
    public static class Core { public static boolean isLobby; }
    public static class Config {
        public boolean vehicleEntityNoItemDrops;
        static final Config INSTANCE = new Config();
        public static Config instance() { return INSTANCE; }
    }
    public static class Vehicle { }
    public static class Stack { }
    public static class Drop { }
    private Object instance;
    private Method handler;

    @BeforeEach void isolate() throws Exception {
        String source = "org/agmas/noellesroles/mixin/VehicleEntityNoDropMixin";
        var node = new ClassNode();
        try (var in = getClass().getResourceAsStream("/" + source + ".class")) {
            assertNotNull(in);
            new ClassReader(in).accept(node, 0);
        }
        node.access &= ~Opcodes.ACC_ABSTRACT;
        node.visibleAnnotations = null;
        node.invisibleAnnotations = null;
        var writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(Map.of(
                source, "org/agmas/noellesroles/mixin/VehicleDropProbe",
                "io/wifi/starrailexpress/SRE", Type.getInternalName(Core.class),
                "io/wifi/starrailexpress/SREConfig", Type.getInternalName(Config.class),
                "net/minecraft/world/entity/vehicle/VehicleEntity", Type.getInternalName(Vehicle.class),
                "net/minecraft/world/item/ItemStack", Type.getInternalName(Stack.class),
                "net/minecraft/world/entity/item/ItemEntity", Type.getInternalName(Drop.class)))));
        var type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { byte[] bytes = writer.toByteArray(); return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        instance = type.getConstructor().newInstance();
        handler = type.getDeclaredMethod("destroy", Vehicle.class, Stack.class, Operation.class);
        handler.setAccessible(true);
    }

    @Test void lobbyAlwaysUsesVanillaDropsAndTrainHonorsConfig() throws Exception {
        for (boolean lobby : new boolean[]{false, true}) {
            for (boolean disabled : new boolean[]{false, true}) {
                Core.isLobby = lobby;
                Config.INSTANCE.vehicleEntityNoItemDrops = disabled;
                var vehicle = new Vehicle();
                var stack = new Stack();
                var drop = new Drop();
                var calls = new AtomicInteger();
                Operation<Drop> original = arguments -> {
                    calls.incrementAndGet();
                    assertArrayEquals(new Object[]{vehicle, stack}, arguments);
                    return drop;
                };
                Object result = handler.invoke(instance, vehicle, stack, original);
                boolean allowed = lobby || !disabled;
                assertEquals(allowed ? 1 : 0, calls.get());
                assertSame(allowed ? drop : null, result);
            }
        }
    }
}
