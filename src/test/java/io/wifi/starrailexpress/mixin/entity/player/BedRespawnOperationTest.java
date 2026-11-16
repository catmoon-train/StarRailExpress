package io.wifi.starrailexpress.mixin.entity.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 执行实际床出生点回调，验证实例接收对象与五个原参数完整转交。 */
public class BedRespawnOperationTest {
    public static class Core { public static boolean isLobby; }
    public static class Player { }
    public static class Dimension { }
    public static class Pos { }
    private Object instance;
    private Method handler;

    @BeforeEach void isolate() throws Exception {
        String source = "io/wifi/starrailexpress/mixin/entity/player/ServerPlayerEntityMixin";
        String probe = "io/wifi/starrailexpress/mixin/entity/player/BedRespawnProbe";
        var node = new ClassNode();
        try (var in = getClass().getResourceAsStream("/" + source + ".class")) {
            assertNotNull(in);
            new ClassReader(in).accept(node, 0);
        }
        node.methods.removeIf(method -> !method.name.equals("<init>") && !method.name.equals("tmm$disableSetSpawnpoint"));
        node.visibleAnnotations = null;
        node.invisibleAnnotations = null;
        var writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(Map.of(
                source, probe, "io/wifi/starrailexpress/SRE", Type.getInternalName(Core.class),
                "net/minecraft/server/level/ServerPlayer", Type.getInternalName(Player.class),
                "net/minecraft/resources/ResourceKey", Type.getInternalName(Dimension.class),
                "net/minecraft/core/BlockPos", Type.getInternalName(Pos.class)))));
        var type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { byte[] bytes = writer.toByteArray(); return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        instance = type.getConstructor().newInstance();
        handler = type.getMethod("tmm$disableSetSpawnpoint", Player.class, Dimension.class, Pos.class,
                float.class, boolean.class, boolean.class, Operation.class);
        Core.isLobby = false;
    }

    @Test void lobbyForwardsReceiverAndAllArgumentsExactlyOnce() throws Exception {
        Core.isLobby = true;
        var player = new Player(); var dimension = new Dimension(); var pos = new Pos();
        var calls = new AtomicInteger();
        Operation<Void> original = arguments -> {
            calls.incrementAndGet();
            assertArrayEquals(new Object[]{player, dimension, pos, 45.5F, true, false}, arguments);
            return null;
        };
        handler.invoke(instance, player, dimension, pos, 45.5F, true, false, original);
        assertEquals(1, calls.get());
    }

    @Test void nullablePositionAndBooleanValuesArePreserved() throws Exception {
        Core.isLobby = true;
        var player = new Player(); var dimension = new Dimension();
        var calls = new AtomicInteger();
        Operation<Void> original = arguments -> {
            calls.incrementAndGet();
            assertArrayEquals(new Object[]{player, dimension, null, -90.0F, false, true}, arguments);
            return null;
        };
        handler.invoke(instance, player, dimension, null, -90.0F, false, true, original);
        assertEquals(1, calls.get());
    }

    @Test void trainStillDoesNotSetRespawnPoint() throws Exception {
        var calls = new AtomicInteger();
        Operation<Void> original = arguments -> { calls.incrementAndGet(); return null; };
        handler.invoke(instance, new Player(), new Dimension(), new Pos(), 0.0F, false, true, original);
        assertEquals(0, calls.get());
    }
}
