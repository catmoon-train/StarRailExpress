package io.wifi.srepackcompat.particle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ParticleCoreTickSafetyTest {
    static class AsyncEngine {
        private static final Set<Class<?>> unsafeParticles = ConcurrentHashMap.newKeySet();
    }
    static class EngineSubclass extends AsyncEngine { }
    static class DisabledEngine { }
    static class UnsupportedEngine {
        private static final String unsafeParticles = "not a set";
    }
    static class Foam { }
    static class OtherParticle { }

    @BeforeEach
    void reset() {
        AsyncEngine.unsafeParticles.clear();
    }

    @Test
    void registersBeforeFirstWorkerTick() {
        assertTrue(ParticleCoreTickSafety.markUnsafe(AsyncEngine.class, Foam.class));
        assertTrue(AsyncEngine.unsafeParticles.contains(Foam.class));
        assertFalse(AsyncEngine.unsafeParticles.contains(OtherParticle.class));
    }

    @Test
    void keepsExistingUnsafeClasses() {
        AsyncEngine.unsafeParticles.add(OtherParticle.class);
        ParticleCoreTickSafety.markUnsafe(AsyncEngine.class, Foam.class);
        assertEquals(Set.of(Foam.class, OtherParticle.class), AsyncEngine.unsafeParticles);
    }

    @Test
    void registrationIsIdempotentAcrossEngineRecreation() {
        ParticleCoreTickSafety.markUnsafe(AsyncEngine.class, Foam.class);
        ParticleCoreTickSafety.markUnsafe(AsyncEngine.class, Foam.class);
        assertEquals(Set.of(Foam.class), AsyncEngine.unsafeParticles);
    }

    @Test
    void supportsEngineSubclassesWithoutDuplicatingState() {
        assertTrue(ParticleCoreTickSafety.markUnsafe(EngineSubclass.class, Foam.class));
        assertEquals(Set.of(Foam.class), AsyncEngine.unsafeParticles);
    }

    @Test
    void disabledAsyncMixinNeedsNoRegistration() {
        assertFalse(ParticleCoreTickSafety.markUnsafe(DisabledEngine.class, Foam.class));
        assertTrue(AsyncEngine.unsafeParticles.isEmpty());
    }

    @Test
    void incompatibleFieldIsReportedRatherThanSilentlyIgnored() {
        assertThrows(IllegalStateException.class,
                () -> ParticleCoreTickSafety.markUnsafe(UnsupportedEngine.class, Foam.class));
    }
}
