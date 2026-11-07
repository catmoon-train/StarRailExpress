package io.wifi.srepackcompat.particle;

import java.lang.reflect.Field;
import java.util.Set;

/** 将有共享可变状态的水面泡沫预登记到 Particle Core 自带的同步回退表。 */
public final class ParticleCoreTickSafety {
    public static final String WATER_FOAM = "org.orecruncher.dsurround.effects.particles.WaterFoam";

    private ParticleCoreTickSafety() { }

    /** 未注入异步优化时没有该字段，此时无需登记；不主动加载或实例化粒子。 */
    public static boolean registerWaterFoam(Class<?> engineClass) {
        try {
            return markUnsafe(engineClass, Class.forName(WATER_FOAM, false, engineClass.getClassLoader()));
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Dynamic Surroundings WaterFoam class is unavailable", error);
        }
    }

    /** 只登记类身份，真正的主线程 tick 和死亡粒子清理由 Particle Core 原流程处理。 */
    @SuppressWarnings("unchecked")
    static boolean markUnsafe(Class<?> engineClass, Class<?> particleClass) {
        for (Class<?> type = engineClass; type != null; type = type.getSuperclass()) {
            final Field field;
            try {
                field = type.getDeclaredField("unsafeParticles");
            } catch (NoSuchFieldException absent) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (!(value instanceof Set<?>)) {
                    throw new IllegalStateException("Unsupported Particle Core unsafeParticles field");
                }
                ((Set<Class<?>>) value).add(particleClass);
                return true;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot register synchronous WaterFoam ticking", error);
            }
        }
        return false;
    }
}
