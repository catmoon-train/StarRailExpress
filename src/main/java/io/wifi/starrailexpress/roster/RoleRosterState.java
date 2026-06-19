package io.wifi.starrailexpress.roster;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 职业轮换系统的全局配置状态。
 * <p>
 * {@link #enabled} 为 true 时，该名单会接管游戏内的职业分配；
 * {@link #roleCounts} 是职业 ResourceLocation 字符串到“本局最大数量”的映射，
 * 数量 {@code <= 0} 表示该职业不在本轮名单中。
 */
public final class RoleRosterState {
    public boolean enabled = false;
    public Map<String, Integer> roleCounts = new LinkedHashMap<>();
    public long version = 0L;

    public static RoleRosterState createDefault() {
        return new RoleRosterState();
    }

    /** 返回归一化后的副本，剔除非法（数量 <= 0）的条目。 */
    public RoleRosterState normalized() {
        if (roleCounts == null) {
            roleCounts = new LinkedHashMap<>();
        }
        roleCounts.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0);
        return this;
    }

    public int countFor(String roleId) {
        Integer value = roleCounts == null ? null : roleCounts.get(roleId);
        return value == null ? 0 : Math.max(0, value);
    }

    public RoleRosterState copy() {
        RoleRosterState copy = new RoleRosterState();
        copy.enabled = this.enabled;
        copy.version = this.version;
        copy.roleCounts = new LinkedHashMap<>(this.roleCounts == null ? Map.of() : this.roleCounts);
        return copy;
    }
}
