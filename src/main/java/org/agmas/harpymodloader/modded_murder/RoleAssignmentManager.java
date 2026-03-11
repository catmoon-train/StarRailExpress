package org.agmas.harpymodloader.modded_murder;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.game.StarRailMurderGameMode;
import io.wifi.starrailexpress.game.StarRailMurderGameMode.RoleInstant;

import org.agmas.harpymodloader.Harpymodloader;

import java.util.*;
import net.minecraft.world.entity.player.Player;

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
    public static Role getCompanionRole(Role role) {
        return Harpymodloader.Occupations_Roles.get(role);
    }

    /**
     * 检查一个角色是否有关联角色
     */
    public static boolean hasCompanionRole(Role role) {
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
    public static boolean tryRemoveARole(Role companion, List<RoleInstant> expandedRoles, List<Role> companionRoles,
            List<Role> companedRoles, int tryLevel) {
        final boolean[] isRemoved = { false };

        expandedRoles.removeIf(ro -> {
            var r = ro.role();
            if (!isRemoved[0]) {
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
    public static List<StarRailMurderGameMode.RoleInstant> expandWithCompanionRoles(
            List<StarRailMurderGameMode.RoleInstant> roles) {
        List<StarRailMurderGameMode.RoleInstant> oldRoles = new ArrayList<>(roles);
        List<StarRailMurderGameMode.RoleInstant> expandedRoles = new ArrayList<>(roles);
        List<Role> companionRoles = new ArrayList<>();
        List<Role> companedRoles = new ArrayList<>();

        for (var role : oldRoles) {
            Role companion = getCompanionRole(role.role());
            if (companion != null) {
                companedRoles.add(role.role());
                int removeCount = role.role().getOccupiedRoleCount();
                if (removeCount < 0)
                    removeCount = 0;
                for (int i = 0; i < removeCount; i++) {
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
                companionRoles.stream().map(r -> new StarRailMurderGameMode.RoleInstant(UUID.randomUUID(), r)).toList());
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
    public static void assignRoleWithCompanion(Map<Player, Role> playerToRole, Player player, Role role) {
        playerToRole.put(player, role);

        // 如果该角色有关联角色，需要为其他玩家分配相应的关联角色
        Role companionRole = getCompanionRole(role);
        if (companionRole != null) {
            Harpymodloader.LOGGER.debug(
                    String.format("Role %s has companion role %s",
                            role.getIdentifier(), companionRole.getIdentifier()));
        }
    }

    /**
     * 获取所有角色对应关系
     */
    public static Map<Role, Role> getOccupationsRoles() {
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
    public static void addOccupationRole(Role mainRole, Role companionRole) {
        Harpymodloader.Occupations_Roles.put(mainRole, companionRole);
    }
}
