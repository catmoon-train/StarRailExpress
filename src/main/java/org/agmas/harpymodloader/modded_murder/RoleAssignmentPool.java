package org.agmas.harpymodloader.modded_murder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.Harpymodloader;
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
        availableRoles.removeIf(
                role -> HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString())
                        || role.identifier().equals(TMMRoles.DISCOVERY_CIVILIAN.identifier())
                        || role.identifier().equals(TMMRoles.LOOSE_END.identifier()) ||
                        !filter.test(role));

        // 构建权重映射
        HashMap<SRERole, Float> roleWeights = new HashMap<>();
        for (SRERole role : availableRoles) {
            float weight = 1f;
            if (HarpyModLoaderConfig.HANDLER.instance().useCustomRoleWeights) {
                weight = ModdedWeights.getRoleWeight(role);
                if (weight <= 0)
                    continue;
            }
            roleWeights.put(role, weight);
        }

        // 构建计数映射
        Map<ResourceLocation, Integer> countMap = new HashMap<>();
        for (SRERole role : availableRoles) {
            if (allowUnlimitedRepeats) {
                // 无限模式：使用大数字表示无限
                countMap.put(role.identifier(), Integer.MAX_VALUE);
            } else {
                // 正常模式：使用ROLE_MAX配置或默认值1
                countMap.put(role.identifier(), Harpymodloader.ROLE_MAX.getOrDefault(role.identifier(), 1));
            }
        }

        return new RoleAssignmentPool(poolName, new WeightedUtil<>(roleWeights), countMap, allowUnlimitedRepeats);
    }

    /**
     * 从池中选择一个角色
     * 
     * @return 选中的角色，如果池为空则返回null
     */
    public SRERole selectRole() {
        return selectRoleWithCountCheck();
    }

    /**
     * 从池中批量选择角色
     * 
     * @param count 要选择的角色数量
     * @return 选中的角色列表
     */
    public List<SRERole> selectRoles(int count) {
        final int maxTrial = 3;
        int needCount = count;
        List<SRERole> selected = new ArrayList<>();
        for (int i = 0; i < needCount; i++) {
            for (int j = 0; j < maxTrial; j++) {
                SRERole role = selectRole();
                if (role != null) {
                    int roleOccupiedCount = role.getOccupiedRoleCount();
                    if (roleOccupiedCount <= 0)
                        roleOccupiedCount = 1;
                    if(ignoreeRoleOccupiedCount)
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

    /**
     * 内部方法：根据权重和计数限制选择角色
     */
    private SRERole selectRoleWithCountCheck() {
        if (isEmpty()) {
            return null;
        }

        SRERole selectedRole = roleWeights.selectRandomKeyBasedOnWeights();
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
            return selectRoleWithCountCheck();
        }
    }

    public void setIgnoreRoleOccupiedCount(boolean b) {
        this.ignoreeRoleOccupiedCount = b;
    }
}
