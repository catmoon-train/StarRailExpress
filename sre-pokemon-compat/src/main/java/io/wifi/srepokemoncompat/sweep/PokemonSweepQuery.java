package io.wifi.srepokemoncompat.sweep;

/** 按实体继承身份保护全部宝可梦，不依赖战斗或所有权接口，也不提前加载可选模组。 */
public final class PokemonSweepQuery {
    private static final ClassValue<Boolean> POKEMON_TYPES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (current.getName().equals("com.cobblemon.mod.common.entity.pokemon.PokemonEntity")) return true;
            }
            return false;
        }
    };

    /** true 表示宝可梦及其子类始终受保护；null 表示交由其他实体规则判断。 */
    public static Boolean protectedState(Object entity) {
        return entity != null && POKEMON_TYPES.get(entity.getClass()) ? Boolean.TRUE : null;
    }

    private PokemonSweepQuery() { }
}
