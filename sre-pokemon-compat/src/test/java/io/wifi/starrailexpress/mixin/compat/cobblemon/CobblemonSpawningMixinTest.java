package io.wifi.starrailexpress.mixin.compat.cobblemon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonSpawningMixinTest {
    private static final String CONFIG = "io.wifi.starrailexpress.SREConfig";
    private static final String PACKAGE = "io.wifi.starrailexpress.mixin.compat.cobblemon.";
    private ClassLoader isolated;
    private Object config;

    @BeforeEach
    void isolateServerConfigWithoutStartingMinecraft() throws Exception {
        isolated = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(CONFIG) && !name.startsWith(PACKAGE)
                        && !name.startsWith("io.wifi.starrailexpress.compat.cobblemon.")
                        && !name.startsWith("io.wifi.srepackcompat.cobblemon.")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try {
                        byte[] bytes = name.equals(CONFIG) ? configFixture() : classBytes(name);
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException error) {
                        throw new ClassNotFoundException(name, error);
                    }
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
        config = isolated.loadClass(CONFIG).getMethod("instance").invoke(null);
    }

    @Test
    void defaultFalseStopsNaturalSpawnTick() throws Exception {
        assertTrue(naturalTick().isCancelled());
    }

    @Test
    void trueKeepsOriginalNaturalSpawnTick() throws Exception {
        enabled(true);
        assertFalse(naturalTick().isCancelled());
    }

    @Test
    void reloadSwitchesBothGuardsWithoutRestart() throws Exception {
        for (boolean value : new boolean[]{true, false, true, false}) {
            enabled(value);
            assertEquals(!value, naturalTick().isCancelled());
            assertEquals(!value, spawnAction().isCancelled());
        }
    }

    @Test
    void queuedSpawnReturnsNoEntityWhenDisabled() throws Exception {
        var result = spawnAction();
        assertTrue(result.isCancelled());
        assertNull(result.getReturnValue());
    }

    @Test
    void guardsAreRegisteredForDedicatedAndIntegratedServers() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("sre-pokemon-compat-core.mixins.json")) {
            assertNotNull(stream);
            var root = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String mixin : new String[]{"PlayerSpawnerMixin", "SingleEntitySpawnActionMixin"}) {
                assertTrue(root.getAsJsonArray("mixins").asList().stream().anyMatch(entry -> mixin.equals(entry.getAsString())));
                assertFalse(root.has("client") && root.getAsJsonArray("client").asList().stream().anyMatch(entry -> mixin.equals(entry.getAsString())));
            }
        }
    }

    @Test
    void runtimeCallbacksPauseAndResumeWithoutBlockingOriginalMethods() throws Exception {
        String[][] handlers = {
                {"ServerMechanicsMixins$Party", "sre$party"}, {"ServerMechanicsMixins$Shoulder", "sre$effects"},
                {"ServerMechanicsMixins$Starter", "sre$starter"}, {"VanillaMechanicsMixins$PlayerHooks", "sre$food"},
                {"VanillaMechanicsMixins$PlayerHooks", "sre$invulnerability"}, {"VanillaMechanicsMixins$Targeting", "sre$target"},
                {"VanillaMechanicsMixins$Villagers", "sre$gather"}, {"VanillaMechanicsMixins$Bartering", "sre$currency"},
                {"VanillaMechanicsMixins$Piglins", "sre$hold"}, {"VanillaMechanicsMixins$Pearls", "sre$pearl"},
                {"VanillaMechanicsMixins$Lecterns", "sre$lectern"}, {"VanillaMechanicsMixins$NoteBlocks", "sre$note"},
                {"VanillaMechanicsMixins$LightningRods", "sre$lightning"}, {"VanillaMechanicsMixins$LightningFire", "sre$fire"},
                {"VanillaMechanicsMixins$Brewing", "sre$automation"}, {"VanillaMechanicsMixins$BrewingIngredient", "sre$ingredient"},
                {"VanillaMechanicsMixins$BrewingBottle", "sre$bottle"}, {"VanillaMechanicsMixins$Phantoms", "sre$fear"},
                {"VanillaMechanicsMixins$Living", "sre$air"}};
        for (boolean value : new boolean[]{false, true, false}) {
            enabled(value);
            for (var handler : handlers) {
                var ci = new CallbackInfo(handler[1], true);
                invoke(handler[0], handler[1], CallbackInfo.class, ci);
                assertEquals(!value, ci.isCancelled(), handler[0]);
            }
        }
    }

    @Test
    void sleepAndBrewingReturnNeutralResultsWhenDisabled() throws Exception {
        var sleep = new CallbackInfoReturnable<Boolean>("didSleep", true);
        invoke("ServerMechanicsMixins$Sleep", "sre$sleep", CallbackInfoReturnable.class, sleep);
        assertTrue(sleep.isCancelled());
        assertFalse(sleep.getReturnValue());
        var brew = new CallbackInfoReturnable<Object>("recipe", true);
        invoke("VanillaMechanicsMixins$Brewing", "sre$recipe", CallbackInfoReturnable.class, brew);
        assertTrue(brew.isCancelled());
        assertNull(brew.getReturnValue());
        enabled(true);
        var allowed = new CallbackInfoReturnable<Object>("recipe", true);
        invoke("VanillaMechanicsMixins$Brewing", "sre$recipe", CallbackInfoReturnable.class, allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void registeredAvoidGoalStopsOnDisableAndRetainsNormalCleanup() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var delegate = new net.minecraft.world.entity.ai.goal.Goal() {
            @Override public boolean canUse() { return true; }
            @Override public boolean canContinueToUse() { return true; }
            @Override public void tick() { calls.incrementAndGet(); }
            @Override public void stop() { calls.addAndGet(10); }
        };
        var type = isolated.loadClass("io.wifi.starrailexpress.compat.cobblemon.CobblemonGatedGoal");
        var goal = (net.minecraft.world.entity.ai.goal.Goal) type.getConstructor(net.minecraft.world.entity.ai.goal.Goal.class).newInstance(delegate);
        assertFalse(goal.canUse());
        enabled(true);
        assertTrue(goal.canUse());
        goal.tick();
        assertEquals(1, calls.get());
        enabled(false);
        assertFalse(goal.canContinueToUse());
        goal.tick();
        goal.stop();
        assertEquals(11, calls.get());
    }

    private void enabled(boolean value) throws Exception { config.getClass().getField("pokmen").setBoolean(config, value); }

    private CallbackInfo naturalTick() throws Exception {
        var ci = new CallbackInfo("tick", true);
        invoke("PlayerSpawnerMixin", "sre$pauseNaturalSpawning", CallbackInfo.class, ci);
        return ci;
    }

    private CallbackInfoReturnable<Object> spawnAction() throws Exception {
        var ci = new CallbackInfoReturnable<Object>("run", true);
        invoke("SingleEntitySpawnActionMixin", "sre$blockDisabledSpawn", CallbackInfoReturnable.class, ci);
        return ci;
    }

    private void invoke(String name, String handler, Class<?> parameter, Object ci) throws Exception {
        Class<?> type = isolated.loadClass(PACKAGE + name);
        Method method = type.getDeclaredMethod(handler, parameter);
        method.setAccessible(true);
        method.invoke(type.getConstructor().newInstance(), ci);
    }

    private static byte[] classBytes(String name) throws IOException {
        try (var stream = CobblemonSpawningMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream == null) throw new IOException("Missing compiled class: " + name);
            return stream.readAllBytes();
        }
    }

    /** 用最小配置替身隔离 AutoConfig 的游戏启动依赖，执行的仍是正式 Mixin 回调字节码。 */
    private static byte[] configFixture() {
        String name = CONFIG.replace('.', '/');
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "pokmen", "Z", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "INSTANCE", "L" + name + ";", null, null).visitEnd();
        var ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
        var init = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        init.visitCode(); init.visitTypeInsn(Opcodes.NEW, name); init.visitInsn(Opcodes.DUP);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false);
        init.visitFieldInsn(Opcodes.PUTSTATIC, name, "INSTANCE", "L" + name + ";");
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        var instance = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "instance", "()L" + name + ";", null, null);
        instance.visitCode(); instance.visitFieldInsn(Opcodes.GETSTATIC, name, "INSTANCE", "L" + name + ";");
        instance.visitInsn(Opcodes.ARETURN); instance.visitMaxs(0, 0); instance.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
