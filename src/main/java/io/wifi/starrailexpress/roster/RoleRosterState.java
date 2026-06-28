package io.wifi.starrailexpress.roster;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 职业轮换系统的全局配置状态。
 * <p>
 * {@link #enabled} 为 true 时，该名单只接管游戏内职业与修饰符的<strong>启用/禁用</strong>
 * （不再接管数量，也没有随机概率）。{@link #roleCounts} / {@link #modifierCounts} 现在仅作为
 * “在不在名单内”的开关集合：键在映射中（值恒为 1）即表示启用，不在即禁用。
 * 实际刷新数量沿用各职业/修饰符注册时的默认值（defaultMaxCount / MODIFIER_MAX）。
 */
public final class RoleRosterState {
    public boolean enabled = false;
    public Map<String, Integer> roleCounts = new LinkedHashMap<>();
    public Map<String, Integer> modifierCounts = new LinkedHashMap<>();
    public long version = 0L;

    public static RoleRosterState createDefault() {
        return new RoleRosterState();
    }

    /**
     * 就地归一化：剔除非法（值为空或 {@code <= 0}）的条目，并把所有保留条目的值统一钳为 1。
     * 名单只表达启用/禁用，不再有数量概念，因此任何历史中数量 &gt; 1 的条目都会被折叠为开关。
     */
    public RoleRosterState normalized() {
        if (roleCounts == null) {
            roleCounts = new LinkedHashMap<>();
        }
        if (modifierCounts == null) {
            modifierCounts = new LinkedHashMap<>();
        }
        roleCounts.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0);
        modifierCounts.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0);
        roleCounts.replaceAll((k, v) -> 1);
        modifierCounts.replaceAll((k, v) -> 1);
        return this;
    }

    public int countFor(String roleId) {
        Integer value = roleCounts == null ? null : roleCounts.get(roleId);
        return value == null ? 0 : Math.max(0, value);
    }

    public int modifierCountFor(String modifierId) {
        Integer value = modifierCounts == null ? null : modifierCounts.get(modifierId);
        return value == null ? 0 : Math.max(0, value);
    }

    public RoleRosterState copy() {
        RoleRosterState copy = new RoleRosterState();
        copy.enabled = this.enabled;
        copy.version = this.version;
        copy.roleCounts = new LinkedHashMap<>(this.roleCounts == null ? Map.of() : this.roleCounts);
        copy.modifierCounts = new LinkedHashMap<>(this.modifierCounts == null ? Map.of() : this.modifierCounts);
        return copy;
    }
}
