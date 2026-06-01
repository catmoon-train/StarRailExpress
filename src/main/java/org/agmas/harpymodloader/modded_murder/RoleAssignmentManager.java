package org.agmas.harpymodloader.modded_murder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理角色对应关系和配对分配
 * 支持同时分配两个关联角色（例如医生和毒师）
 */
public class RoleAssignmentManager {

    /**
     * 根据 Occupations_Roles 映射获取关联角色
     * 
     * @param role 主角色
     * @return 关联的角色，如果没有则返回null
     */
    public static SRERole getCompanionRole(SRERole role) {
        return Harpymodloader.Occupations_Roles.get(role);
    }

    /**
     * 检查一个角色是否有关联角色
     */
    public static boolean hasCompanionRole(SRERole role) {
        return Harpymodloader.Occupations_Roles.containsKey(role);
    }

    /**
     * 
     * @param companion
     * @param expandedRoles
     * @param companionRoles
     * @param companedRoles
     * @param tryLevel       尝试匹配等级。0：完全相同，1：忽略中立阵营，2：包含平民，3：包括所有
     * @return
     */
    public static boolean tryRemoveARole(SRERole companion, List<RoleInstance> expandedRoles,
            List<SRERole> companionRoles,
            List<SRERole> companedRoles, int tryLevel) {
        final boolean[] isRemoved = { false };

        expandedRoles.removeIf(ro -> {
            if (!isRemoved[0]) {
                var r = ro.role();
                boolean conditionMet = false;
                if (tryLevel == 0) {
                    conditionMet = (PlayerRoleWeightManager
                            .getRoleType(r) == PlayerRoleWeightManager
                                    .getRoleType(companion));
                } else if (tryLevel == 1) {
                    conditionMet = (PlayerRoleWeightManager
                            .getRoleType_IgnoreNeutralType(r) == PlayerRoleWeightManager
                                    .getRoleType_IgnoreNeutralType(companion));
                } else if (tryLevel == 2) {
                    conditionMet = (PlayerRoleWeightManager
                            .getRoleType_OnlyDistinctKiller(r) == PlayerRoleWeightManager
                                    .getRoleType(companion));
                } else {
                    conditionMet = true;
                }

                if (conditionMet && companionRoles.stream()
                        .noneMatch(rd -> rd.getIdentifier().equals(r.getIdentifier()))
                        && companedRoles.stream()
                                .noneMatch(rd -> rd.getIdentifier().equals(r.getIdentifier()))) {
                    isRemoved[0] = true;
                    return true;
                }
            }
            return false;
        });
        return isRemoved[0];
    }

    /**
     * 展开角色列表：如果角色有关联角色，添加关联角色
     * 例如：[医生] -> [医生, 毒师]
     * 注意：允许结果列表中包含重复的角色
     * 
     * @param roles 原始角色列表
     * @return 展开后的角色列表（包含所有关联角色，允许重复）
     */
    public static List<RoleInstance> expandWithCompanionRoles(
            List<RoleInstance> roles) {
        List<RoleInstance> oldRoles = new ArrayList<>(roles);
        List<RoleInstance> expandedRoles = new ArrayList<>(roles);
        List<SRERole> companionRoles = new ArrayList<>();
        List<SRERole> companedRoles = new ArrayList<>();

        for (var role : oldRoles) {
            SRERole companion = getCompanionRole(role.role());
            if (companion != null) {
                companedRoles.add(role.role());
                {
                    if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 0)) {
                        if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 1)) {
                            if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 2)) {
                                if (!tryRemoveARole(companion, expandedRoles, companionRoles, companedRoles, 3)) {
                                    Harpymodloader.LOGGER
                                            .error("Unable to remove a role to make room for linked role {}!",
                                                    role.role().identifier().toString());
                                }
                            }
                        }
                    }
                }
                companionRoles.add(companion);
            }
        }

        expandedRoles.addAll(
                companionRoles.stream().map(r -> new RoleInstance(UUID.randomUUID(), r))
                        .toList());
        return expandedRoles;
    }

    /**
     * 将角色分配给玩家，同时处理关联角色
     * 如果玩家已经有角色，则使用 expandWithCompanionRoles 来确保关联角色也被分配
     * 
     * @param playerToRole 玩家到角色的映射
     * @param player       玩家
     * @param role         要分配的角色
     */
    public static void assignRoleWithCompanion(Map<Player, SRERole> playerToRole, Player player, SRERole role) {
        playerToRole.put(player, role);

        // 如果该角色有关联角色，需要为其他玩家分配相应的关联角色
        SRERole companionRole = getCompanionRole(role);
        if (companionRole != null) {
            Harpymodloader.LOGGER.debug(
                    String.format("Role %s has companion role %s",
                            role.getIdentifier(), companionRole.getIdentifier()));
        }
    }

    /**
     * 获取所有角色对应关系
     */
    public static Map<SRERole, SRERole> getOccupationsRoles() {
        return Harpymodloader.Occupations_Roles;
    }

    /**
     * 清空所有角色对应关系
     */
    public static void clearOccupationsRoles() {
        Harpymodloader.Occupations_Roles.clear();
    }

    /**
     * 添加角色对应关系
     */
    public static void addOccupationRole(SRERole mainRole, SRERole companionRole) {
        Harpymodloader.Occupations_Roles.put(mainRole, companionRole);
    }

    public static List<RoleInstance> removeOpposingJobs(List<RoleInstance> roles, RoleAssignmentPool killerPool,
            RoleAssignmentPool neutralsPool,
            RoleAssignmentPool vigilantePool, RoleAssignmentPool civilianPool, boolean haveOccupationRoles,
            int maxDepth) {
        int neutrals = 0, civilian = 0, killer = 0, neturals_for_killer = 0, vigilante = 0;
        List<SRERole> all_roles = new ArrayList<>();
        for (var rolei : roles) {
            all_roles.add(rolei.role());
        }
        List<SRERole> new_all_roles = new ArrayList<>(all_roles);

        for (var role : all_roles) {
            if (!new_all_roles.contains(role))
                continue;
            for (var oprole : role.opposingJobs) {
                if (new_all_roles.contains(oprole)) {
                    int roleType = oprole.getRoleType();
                    while (new_all_roles.remove(oprole)) {
                        // -1: Unknown - 1: Innocent - 2: Neturals but not for killer - 3: Neturals for
                        // killer - 4: Killer - 5: Vigilante
                        switch (roleType) {
                            case 1: {
                                civilian++;
                                break;
                            }
                            case 2: {
                                neutrals++;
                                break;
                            }
                            case 3: {
                                neturals_for_killer++;
                                break;
                            }
                            case 4: {
                                killer++;
                                break;
                            }
                            case 5: {
                                vigilante++;
                                break;
                            }
                            default:
                                civilian++;
                        }
                    }
                }
            }
        }
        List<RoleInstance> all_role_instances = new ArrayList<>();
        for (var r : new_all_roles) {
            all_role_instances.add(new RoleInstance(UUID.randomUUID(), r));
        }
        if (maxDepth <= 0) {
            return all_role_instances;
        }
        int all = killer + civilian + neutrals + neturals_for_killer + vigilante;
        all_role_instances.addAll(SREMurderGameMode.getAllRoles(killer, vigilante, neutrals + neturals_for_killer, all,
                0, killerPool, neutralsPool, vigilantePool, civilianPool, haveOccupationRoles, maxDepth - 1));
        return all_role_instances;
    }
}
