package io.wifi.srepokemoncompat.sweep;

import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** 仅对已存在的宝可梦实例解析公开接口，不提前加载可选模组。 */
public final class PokemonSweepQuery {
    private static final Api NONE = new Api(null, null, null);
    private static final Api UNSUPPORTED = new Api(null, null, null);
    private static final ClassValue<Api> APIS = new ClassValue<>() {
        @Override protected Api computeValue(Class<?> type) {
            Class<?> current = type;
            while (current != null && !current.getName().equals("com.cobblemon.mod.common.entity.pokemon.PokemonEntity")) {
                current = current.getSuperclass();
            }
            if (current == null) return NONE;
            try { return bind(current); }
            catch (ReflectiveOperationException error) {
                LoggerFactory.getLogger("sre-pokemon-compat/aotake").warn(
                        "Cobblemon sweep API unavailable; protecting Pokemon instead of deleting them", error);
                return UNSUPPORTED;
            }
        }
    };

    /** null 表示不是宝可梦；NPC 所有权不作为玩家所有权处理。 */
    public static Boolean protectedState(Object entity) {
        Api api = APIS.get(entity.getClass());
        return api == NONE ? null : api.test(entity);
    }

    static Api bind(Class<?> entityClass) throws ReflectiveOperationException {
        var lookup = MethodHandles.publicLookup();
        var getter = entityClass.getMethod("getPokemon");
        return new Api(
                lookup.unreflect(entityClass.getMethod("isBattling")).asType(MethodType.methodType(boolean.class, Object.class)),
                lookup.unreflect(getter).asType(MethodType.methodType(Object.class, Object.class)),
                lookup.unreflect(getter.getReturnType().getMethod("isPlayerOwned"))
                        .asType(MethodType.methodType(boolean.class, Object.class)));
    }

    record Api(MethodHandle battling, MethodHandle pokemon, MethodHandle playerOwned) {
        boolean test(Object entity) {
            if (battling == null) return true;
            try {
                if ((boolean) battling.invokeExact(entity)) return true;
                Object data = (Object) pokemon.invokeExact(entity);
                return (boolean) playerOwned.invokeExact(data);
            } catch (Throwable error) {
                throw new IllegalStateException("Cannot inspect Cobblemon sweep protection", error);
            }
        }
    }
    private PokemonSweepQuery() { }
}
