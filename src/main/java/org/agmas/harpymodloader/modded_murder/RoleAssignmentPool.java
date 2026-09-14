/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.harpymodloader.modded_murder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.WeightedUtil;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 模块化角色分配池 - 处理通用的角色选择和计数逻辑
 * 避免重复代码，提高可维护性
 */
public class RoleAssignmentPool {
    /** 强制职业的权重相对其余职业之和的倍数，保证它几乎一定先被抽中。 */
    private static final float FORCED_WEIGHT_FACTOR = 1000f;

    private final WeightedUtil<SRERole> roleWeights;
    private final Map<ResourceLocation, Integer> roleCountMap;
    private final String poolName;
    private final boolean allowUnlimitedRepeats;
    public boolean ignoreeRoleOccupiedCount = false;

    private RoleAssignmentPool(String poolName, WeightedUtil<SRERole> roleWeights,
            Map<ResourceLocation, Integer> roleCountMap,
            boolean allowUnlimitedRepeats) {
        this.poolName = poolName;
        this.roleWeights = roleWeights;
        this.roleCountMap = roleCountMap;
        this.allowUnlimitedRepeats = allowUnlimitedRepeats;
    }

    /**
     * 创建一个角色分配池
     * 
     * @param poolName 池的名称（用于日志）
     * @param filter   角色过滤条件（返回true表示该角色应该被包含在池中）
     * @return 创建的RoleAssignmentPool实例
     */
    public static RoleAssignmentPool create(String poolName, Predicate<SRERole> filter) {
        return createInternal(poolName, filter, false);
    }

    /**
     * 创建一个支持无限重复的角色分配池
     * 同一个角色可以被多次选中
     * 
     * @param poolName 池的名称（用于日志）
     * @param filter   角色过滤条件（返回true表示该角色应该被包含在池中）
     * @return 创建的RoleAssignmentPool实例
     */
    public static RoleAssignmentPool createUnlimited(String poolName, Predicate<SRERole> filter) {
        return createInternal(poolName, filter, true);
    }

    /**
     * 内部方法：创建角色分配池
     */
    private static RoleAssignmentPool createInternal(String poolName, Predicate<SRERole> filter,
            boolean allowUnlimitedRepeats) {
        // 获取所有符合条件的角色
        ArrayList<SRERole> availableRoles = new ArrayList<>(TMMRoles.ROLES.values());
        availableRoles.removeIf(role -> {
            if (role.identifier().equals(TMMRoles.DISCOVERY_CIVILIAN.identifier())
                    || role.identifier().equals(TMMRoles.LOOSE_END.identifier())
                    || !filter.test(role)) {
                return true;
            }
            // 统一API处理
            return SREDisableManager.isRoleDisabled(role);
        });

        // 构建权重映射
        HashMap<SRERole, Float> roleWeights = new HashMap<>();
        HashMap<SRERole, Float> forcedRoles = new HashMap<>();
        for (SRERole role : availableRoles) {
            boolean forced = isMapForced(role);
            float weight = 1f;
            if (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights) {
                weight = ModdedWeights.getRoleWeight(role);
                if (weight <= 0) {
                    if (!forced)
                        continue;
                    // 被地图强制的职业即使自定义权重为 0 也要留在池里
                    weight = 1f;
                }
            }
            roleWeights.put(role, weight);
            if (forced) {
                forcedRoles.put(role, weight);
            }
        }
        applyForcedWeights(roleWeights, forcedRoles);

        // 构建计数映射
        Map<ResourceLocation, Integer> countMap = new HashMap<>();
        for (SRERole role : availableRoles) {
            if (allowUnlimitedRepeats) {
                // 无限模式：使用大数字表示无限
                countMap.put(role.identifier(), Integer.MAX_VALUE);
            } else {
                // 正常模式：使用ROLE_MAX配置或默认值1
                int count = Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1);
                // 地图 enabled/forced 的职业必须能进池：即使 ROLE_MAX 被后续逻辑清成 0 也保证 1 个
                if (count <= 0 && isMapEnabled(role)) {
                    count = 1;
                }
                countMap.put(role.identifier(), count);
            }
        }

        return new RoleAssignmentPool(poolName, new WeightedUtil<>(roleWeights), countMap, allowUnlimitedRepeats);
    }

    /** 地图 enabledRoles / forcedRoles 命中的职业（必须进入选择池）。 */
    private static boolean isMapEnabled(SRERole role) {
        var enabled = Harpymodloader.MAP_ENABLED_ROLES;
        return enabled != null && enabled.contains(role.identifier());
    }

    /** 地图 forcedRoles 命中的职业（除进池外权重拉满）。 */
    private static boolean isMapForced(SRERole role) {
        var forced = Harpymodloader.MAP_FORCED_ROLES;
        return forced != null && !forced.isEmpty() && forced.contains(role.identifier());
    }

    /**
     * 把强制职业的权重抬到远大于其余职业之和，使其在池内优先被抽中（"最大可能被选择"）。
     * 只影响抽取概率，不影响 opposing / 关联职业等后续逻辑。
     */
    private static void applyForcedWeights(HashMap<SRERole, Float> roleWeights,
            HashMap<SRERole, Float> forcedRoles) {
        if (forcedRoles.isEmpty())
            return;
        float othersSum = 0f;
        for (var entry : roleWeights.entrySet()) {
            if (!forcedRoles.containsKey(entry.getKey())) {
                othersSum += entry.getValue();
            }
        }
        float forcedWeight = Math.max(1f, othersSum * FORCED_WEIGHT_FACTOR + 1f);
        for (var entry : forcedRoles.entrySet()) {
            roleWeights.put(entry.getKey(), Math.max(entry.getValue(), forcedWeight));
        }
    }

    /**
     * 从池中选择一个角色
     * 
     * @return 选中的角色，如果池为空则返回null
     */
    public SRERole selectRole() {
        return selectRole((r) -> true);
    }

    /**
     * 从池中选择一个角色
     * 
     * @return 选中的角色，如果池为空则返回null
     */
    public SRERole selectRole(Predicate<SRERole> condition) {
        return selectRoleWithCountCheck(condition);
    }

    /**
     * 从池中批量选择角色
     * 
     * @param count 要选择的角色数量
     * @return 选中的角色列表
     */
    public List<SRERole> selectRoles(int count) {
        return selectRoles(count, (r) -> true);
    }

    public List<SRERole> selectRoles(int count, Predicate<SRERole> condition) {
        final int maxTrial = 3;
        int needCount = count;
        List<SRERole> selected = new ArrayList<>();
        for (int i = 0; i < needCount; i++) {
            for (int j = 0; j < maxTrial; j++) {
                SRERole role = selectRole(condition);
                if (role != null) {
                    int roleOccupiedCount = role.getOccupiedRoleCount();
                    // 额外逻辑：occupiedRoleCount <= 0 表示不占用角色槽位（如迷失杀手）
                    // 选中此角色但 needCount 不变，使其不占用正常杀手名额
                    if (roleOccupiedCount <= 0) {
                        selected.add(role);
                        break;
                    }
                    if (ignoreeRoleOccupiedCount)
                        roleOccupiedCount = 1;
                    if (i + roleOccupiedCount <= needCount) {
                        selected.add(role);
                        needCount = needCount - (roleOccupiedCount - 1);
                        break;
                    } else {
                        if (selected.size() > roleOccupiedCount - 1) {
                            for (int k = 0; k < roleOccupiedCount - 1; k++) {
                                selected.remove(0);
                            }
                            selected.add(role);
                            break;
                        }
                    }
                }
            }
        }
        return selected;
    }

    /**
     * 检查池中是否还有可用的角色
     */
    public boolean isEmpty() {
        return roleWeights.isEmpty();
    }

    /**
     * 获取池中剩余的角色数量
     */
    public int getRemainingCount() {
        return roleCountMap.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 获取池的名称
     */
    public String getPoolName() {
        return poolName;
    }

    public Map<ResourceLocation, Integer> getRoleCountMap() {
        return roleCountMap;
    }

    /**
     * 内部方法：根据权重和计数限制选择角色
     */
    private SRERole selectRoleWithCountCheck(Predicate<SRERole> condition) {
        if (isEmpty()) {
            return null;
        }
        var roleWeights2 = roleWeights.filter(condition);
        SRERole selectedRole = roleWeights2.selectRandomKeyBasedOnWeights();
        if (selectedRole == null) {
            return null;
        }

        int remainingCount = roleCountMap.getOrDefault(selectedRole.identifier(), 1);
        if (remainingCount > 0) {
            // 在无限重复模式下，不减少计数
            if (!allowUnlimitedRepeats) {
                roleCountMap.put(selectedRole.identifier(), remainingCount - 1);
                if (remainingCount - 1 <= 0) {
                    roleWeights.removeKey(selectedRole);
                }
            }
            return selectedRole;
        } else {
            roleWeights.removeKey(selectedRole);
            return selectRoleWithCountCheck(condition);
        }
    }

    public void setIgnoreRoleOccupiedCount(boolean b) {
        this.ignoreeRoleOccupiedCount = b;
    }

    public void addRoleCount(SRERole role, int i) {
        if (role == null)
            return;
        int remainingCount = roleCountMap.getOrDefault(role.identifier(), 1);
        if (remainingCount + i >= 0) {
            // 在无限重复模式下，不减少计数
            if (!allowUnlimitedRepeats) {
                roleCountMap.put(role.identifier(), remainingCount + i);
                if (remainingCount + i <= 0) {
                    roleWeights.removeKey(role);
                }
            }
            return;
        } else {
            roleWeights.removeKey(role);
            return;
        }
    }

    public void removeRoleCount(SRERole role, int i) {
        addRoleCount(role, -i);
    }
}
