package io.wifi.starrailexpress.content.item;

public class SREItemProperties {
    /**
     * 可以左键攻击玩家，但不会死
     */
    public interface LeftClickHurtable {
    }
    /**
     * 可以左键攻击玩家，且会死
     */
    public interface LeftClickKillable extends LeftClickHurtable {
    }
}
