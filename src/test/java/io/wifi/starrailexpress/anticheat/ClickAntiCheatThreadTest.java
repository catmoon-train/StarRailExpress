package io.wifi.starrailexpress.anticheat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 执行正式反作弊字节码，轻量替身只隔离服务器线程、配置和玩家接口。 */
public class ClickAntiCheatThreadTest {
    public enum Click { LEFT, RIGHT }
    public static class Config {
        static final Config CURRENT = new Config();
        static int reads;
        public boolean enableClickAc = true;
        public static Config instance() { reads++; return CURRENT; }
    }
    public static class Server {
        final Thread owner = Thread.currentThread();
        public boolean isSameThread() { return Thread.currentThread() == owner; }
    }
    public static class Level {
        long tick;
        public long getGameTime() { return tick; }
    }
    public static class Player {
        public final Server server = new Server();
        final Level level = new Level();
        final UUID id = UUID.randomUUID();
        boolean creative, spectator, operator;
        public boolean isCreative() { return creative; }
        public boolean isSpectator() { return spectator; }
        public boolean hasPermissions(int permission) { return operator; }
        public UUID getUUID() { return id; }
        public Level serverLevel() { return level; }
    }

    private Class<?> type;
    private Method click, locked;
    private Player player;
    private Map<?, ?> states;

    @BeforeEach void isolate() throws Exception {
        String source = "io/wifi/starrailexpress/anticheat/ClickAntiCheat";
        String probe = "io/wifi/starrailexpress/anticheat/ThreadProbe";
        var names = Map.of(source, probe, source + "$PlayerClickState", probe + "$PlayerClickState",
                source + "$ClickType", Type.getInternalName(Click.class),
                "io/wifi/starrailexpress/SREConfig", Type.getInternalName(Config.class),
                "net/minecraft/server/level/ServerPlayer", Type.getInternalName(Player.class),
                "net/minecraft/server/MinecraftServer", Type.getInternalName(Server.class),
                "net/minecraft/server/level/ServerLevel", Type.getInternalName(Level.class));
        var classes = new HashMap<String, byte[]>();
        for (String name : new String[]{source, source + "$PlayerClickState"}) {
            try (var in = getClass().getResourceAsStream("/" + name + ".class")) {
                assertNotNull(in);
                var writer = new ClassWriter(0);
                new ClassReader(in).accept(new ClassRemapper(writer, new SimpleRemapper(names)), 0);
                classes.put(names.get(name).replace('/', '.'), writer.toByteArray());
            }
        }
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        type = loader.loadClass(probe.replace('/', '.'));
        click = type.getMethod("onClick", Player.class, Click.class);
        locked = type.getMethod("isLocked", Player.class);
        var field = type.getDeclaredField("STATES"); field.setAccessible(true);
        states = (Map<?, ?>) field.get(null);
        player = new Player();
        Config.CURRENT.enableClickAc = true;
        Config.reads = 0;
    }

    private boolean click(Click side) throws Exception { return (boolean) click.invoke(null, player, side); }
    private Object state() { return states.get(player.id); }
    private int recorded() throws Exception {
        var field = state().getClass().getDeclaredField("clickTimes"); field.setAccessible(true);
        return ((ArrayDeque<?>) field.get(state())).size();
    }
    private void setLocked() throws Exception {
        var field = state().getClass().getDeclaredField("lockUntilMs"); field.setAccessible(true);
        field.setLong(state(), System.currentTimeMillis() + 60_000L);
    }

    @Test void nullPlayerReturnsBeforeReadingConfiguration() throws Exception {
        assertEquals(false, click.invoke(null, null, Click.LEFT));
        assertEquals(false, locked.invoke(null, (Object) null));
        assertEquals(0, Config.reads);
    }

    @Test void nettyFirstEntryDoesNotReadConfigOrCreateClickState() throws Exception {
        var failure = new AtomicReference<Throwable>();
        Thread netty = new Thread(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    assertFalse(click(Click.LEFT));
                    assertFalse(click(Click.RIGHT));
                    assertEquals(false, locked.invoke(null, player));
                }
            } catch (Throwable error) { failure.set(error); }
        });
        netty.start(); netty.join();
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertEquals(0, Config.reads);
        assertTrue(states.isEmpty());
    }

    @Test void mainThreadStillCountsOncePerSideAndTick() throws Exception {
        assertFalse(click(Click.LEFT));
        assertFalse(click(Click.LEFT));
        assertFalse(click(Click.RIGHT));
        assertEquals(2, recorded());
        player.level.tick++;
        assertFalse(click(Click.LEFT));
        assertEquals(3, recorded());
    }

    @Test void lockedMainThreadStillRejectsButNettyDoesNotCancelDispatch() throws Exception {
        assertFalse(click(Click.LEFT));
        setLocked();
        assertEquals(true, locked.invoke(null, player));
        assertTrue(click(Click.RIGHT));
        int reads = Config.reads;
        var failure = new AtomicReference<Throwable>();
        Thread netty = new Thread(() -> {
            try {
                assertFalse(click(Click.RIGHT));
                assertEquals(false, locked.invoke(null, player));
            } catch (Throwable error) { failure.set(error); }
        });
        netty.start(); netty.join();
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertEquals(reads, Config.reads);
        assertEquals(1, recorded());
    }

    @Test void disabledAndExemptPlayersDoNotCreateState() throws Exception {
        Config.CURRENT.enableClickAc = false;
        assertFalse(click(Click.LEFT));
        Config.CURRENT.enableClickAc = true;
        player.creative = true; assertFalse(click(Click.LEFT)); player.creative = false;
        player.spectator = true; assertFalse(click(Click.LEFT)); player.spectator = false;
        player.operator = true; assertFalse(click(Click.LEFT));
        assertTrue(states.isEmpty());
    }

    @Test void disconnectRemovesStateWithoutChangingOtherPlayers() throws Exception {
        assertFalse(click(Click.LEFT));
        type.getMethod("onPlayerDisconnect", UUID.class).invoke(null, player.id);
        assertTrue(states.isEmpty());
    }
}
