package io.wifi.starrailexpress.compat.cobblemon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

/** 延迟解析可选模组的错误结果，返回上游支持的失败对象，而不是 null 或伪造成功。 */
public final class CobblemonBattleControl {
    private static final class Api {
        private static final Constructor<?> RESULT;
        private static final Method ERRORS;
        private static final Object REASON;
        static {
            try {
                Class<?> result = Class.forName("com.cobblemon.mod.common.battles.ErroredBattleStart");
                RESULT = result.getConstructor();
                ERRORS = result.getMethod("getGeneralErrors");
                Object companion = Class.forName("com.cobblemon.mod.common.battles.BattleStartError").getField("Companion").get(null);
                REASON = companion.getClass().getMethod("canceledByEvent", MutableComponent.class)
                        .invoke(companion, Component.translatable("message.starrailexpress.cobblemon_disabled"));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported Cobblemon battle result API", error);
            }
        }
    }

    public static Object disabledResult() {
        try {
            Object result = Api.RESULT.newInstance();
            @SuppressWarnings("unchecked") Set<Object> errors = (Set<Object>) Api.ERRORS.invoke(result);
            errors.add(Api.REASON);
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot reject disabled Cobblemon battle", error);
        }
    }

    public interface BattleAccess { void sre$stopBattle(); }
    private CobblemonBattleControl() { }
}
