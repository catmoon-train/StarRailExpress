package io.wifi.srepackcompat.cobblemon;

/** 每十个世界 tick 保留三次思考，按实体错峰；不影响实体移动和生命周期。 */
public final class PokemonAiBudget {
    private static final int PERIOD = 10;

    public static boolean shouldThink(long gameTime, int entityId) {
        // 先分别取模再相加，避免世界时间或实体 ID 溢出影响调度。
        int phase = (int) ((Math.floorMod(gameTime, PERIOD) + Math.floorMod(entityId, PERIOD)) % PERIOD);
        return phase == 0 || phase == 3 || phase == 6;
    }

    private PokemonAiBudget() { }
}
