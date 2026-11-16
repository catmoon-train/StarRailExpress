package io.wifi.srepackcompat.cobblemon;

import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

/** 只判断是否存在行走进化条件，不排序、不合并集合，也不缓存可变的进化数据。 */
public final class EvolutionRequirementQuery {
    private final MethodHandle form;
    private final MethodHandle evolutions;
    private final MethodHandle requirements;
    private final Class<?> walkingRequirement;

    private static final class OptionalApi {
        private static final EvolutionRequirementQuery QUERY = discover();
    }

    private EvolutionRequirementQuery(MethodHandle form, MethodHandle evolutions,
                                      MethodHandle requirements, Class<?> walkingRequirement) {
        this.form = form;
        this.evolutions = evolutions;
        this.requirements = requirements;
        this.walkingRequirement = walkingRequirement;
    }

    /** null 表示接口不可用，调用方应继续执行上游方法。 */
    public static Boolean query(Object pokemon) {
        var query = OptionalApi.QUERY;
        return query == null ? null : query.hasWalkingRequirement(pokemon);
    }

    static EvolutionRequirementQuery bind(Class<?> pokemon, Class<?> form, Class<?> evolution,
                                          Class<?> walkingRequirement) throws ReflectiveOperationException {
        var lookup = MethodHandles.publicLookup();
        return new EvolutionRequirementQuery(
                lookup.unreflect(pokemon.getMethod("getForm")).asType(MethodType.methodType(Object.class, Object.class)),
                lookup.unreflect(form.getMethod("getEvolutions")).asType(MethodType.methodType(Set.class, Object.class)),
                lookup.unreflect(evolution.getMethod("getRequirements")).asType(MethodType.methodType(Set.class, Object.class)),
                walkingRequirement);
    }

    boolean hasWalkingRequirement(Object pokemon) {
        try {
            Object currentForm = (Object) form.invokeExact(pokemon);
            Set<?> currentEvolutions = (Set<?>) evolutions.invokeExact(currentForm);
            for (Object evolution : currentEvolutions) {
                Set<?> currentRequirements = (Set<?>) requirements.invokeExact(evolution);
                for (Object requirement : currentRequirements) {
                    if (walkingRequirement.isInstance(requirement)) return true;
                }
            }
            return false;
        } catch (Throwable error) {
            // 不吞掉上游 getter 的异常，避免把数据错误伪装成“没有进化条件”。
            if (error instanceof RuntimeException runtime) throw runtime;
            if (error instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Cannot inspect Cobblemon evolution requirements", error);
        }
    }

    private static EvolutionRequirementQuery discover() {
        try {
            return bind(Class.forName("com.cobblemon.mod.common.pokemon.Pokemon"),
                    Class.forName("com.cobblemon.mod.common.pokemon.FormData"),
                    Class.forName("com.cobblemon.mod.common.api.pokemon.evolution.Evolution"),
                    Class.forName("com.cobblemon.mod.common.pokemon.requirements.BlocksTraveledRequirement"));
        } catch (ReflectiveOperationException | RuntimeException error) {
            LoggerFactory.getLogger("sre-pack-compat/evolution").warn(
                    "Evolution query optimization unavailable; retaining original Cobblemon query", error);
            return null;
        }
    }
}
