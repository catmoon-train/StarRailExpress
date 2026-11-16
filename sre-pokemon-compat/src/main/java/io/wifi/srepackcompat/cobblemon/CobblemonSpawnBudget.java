package io.wifi.srepackcompat.cobblemon;

import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BooleanSupplier;

/** 按服务端负载保留 10%–40% 的频率和密度；已有宝可梦不强制清除。 */
public final class CobblemonSpawnBudget {
    private static final AdaptiveSpawnBudget LOAD_BUDGET = new AdaptiveSpawnBudget();

    private static final class ServerSwitch {
        private static final BooleanSupplier ENABLED = discover();
    }

    public static boolean enabled() { return ServerSwitch.ENABLED.getAsBoolean(); }
    public static float scale(float original) { return original * LOAD_BUDGET.fraction(); }
    public static void observeAverageTickNanos(long averageNanos) { LOAD_BUDGET.observeAverageTickNanos(averageNanos); }
    public static void resetLoadBudget() { LOAD_BUDGET.reset(); }

    private static BooleanSupplier discover() {
        // 独立使用时只限量；安装 SRE 时服从其服务端开关，不读取客户端连接状态。
        try {
            return bind(Class.forName("io.wifi.starrailexpress.SREConfig"));
        } catch (ClassNotFoundException absentCore) {
            return () -> true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            LoggerFactory.getLogger("sre-pack-compat/spawning").warn(
                    "SRE spawning switch unavailable; keeping Cobblemon spawning disabled", error);
            return () -> false;
        }
    }

    /** 每次取当前配置实例，以兼容重载时 AutoConfig 替换对象；只解析一次反射接口。 */
    static BooleanSupplier bind(Class<?> configClass) throws ReflectiveOperationException {
        var lookup = MethodHandles.lookup();
        MethodHandle instance = lookup.unreflect(configClass.getMethod("instance"));
        MethodHandle flag = lookup.unreflectGetter(configClass.getField("pokmen"));
        MethodHandle getter = MethodHandles.filterReturnValue(instance, flag).asType(MethodType.methodType(boolean.class));
        return () -> {
            try { return (boolean) getter.invokeExact(); }
            catch (Throwable error) { throw new IllegalStateException("Cannot read SRE server spawning switch", error); }
        };
    }

    private CobblemonSpawnBudget() { }
}
