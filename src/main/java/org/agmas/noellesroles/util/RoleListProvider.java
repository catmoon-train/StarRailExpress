package org.agmas.noellesroles.util;

import io.wifi.starrailexpress.api.SRERole;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色列表提供者
 * 使用本地方法获取可用角色列表
 */
public class RoleListProvider {

    private static boolean initialized = false;
    private static List<String> availableRoles = new ArrayList<>();

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            // 使用本地方法获取当前启用的角色列表
            List<SRERole> roles = Noellesroles.getEnableAndAvailableRoles(false);
            for (SRERole role : roles) {
                if (role != null && role.identifier() != null) {
                    availableRoles.add(role.identifier().toString());
                }
            }
        } catch (Exception e) {
            // 如果获取失败，使用备用列表
            Noellesroles.LOGGER.warn("Failed to get roles from game, using fallback list", e);
        }

        // 如果列表为空，添加备用角色
        if (availableRoles.isEmpty()) {
            addFallbackRoles();
        }
    }

    private static void addFallbackRoles() {
        availableRoles.add("noellesroles:executioner");
        availableRoles.add("noellesroles:swapper");
        availableRoles.add("noellesroles:morphling");
        availableRoles.add("noellesroles:voodoo");
        availableRoles.add("noellesroles:manipulator");
        availableRoles.add("noellesroles:ninja");
        availableRoles.add("noellesroles:stalker");
        availableRoles.add("noellesroles:creeper");
        availableRoles.add("noellesroles:party_killer");
        availableRoles.add("noellesroles:bandit");
        availableRoles.add("noellesroles:watcher");
        availableRoles.add("noellesroles:conspirator");
        availableRoles.add("noellesroles:bomber");
        availableRoles.add("noellesroles:delayer");
        availableRoles.add("noellesroles:insane_killer");
        availableRoles.add("noellesroles:imitator");
        availableRoles.add("noellesroles:shadow_falcon");
        availableRoles.add("noellesroles:spellbreaker");
        availableRoles.add("noellesroles:dio");
        availableRoles.add("noellesroles:ma_chen_xu");
        availableRoles.add("noellesroles:water_ghost");
        // 添加 StarRailExpress 原版角色
        availableRoles.add("starrailexpress:vigilante");
        availableRoles.add("starrailexpress:civilian");
        availableRoles.add("starrailexpress:discovery_civilian");
        availableRoles.add("starrailexpress:loose_end");
    }

    public static boolean hasRoleListProvider() {
        initialize();
        return !availableRoles.isEmpty();
    }

    public static List<String> getAvailableRoles() {
        initialize();
        return new ArrayList<>(availableRoles);
    }
}
