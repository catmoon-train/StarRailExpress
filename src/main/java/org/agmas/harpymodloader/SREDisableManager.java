package org.agmas.harpymodloader;

import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.data.ClientRoleRosterCache;
import io.wifi.starrailexpress.roster.RoleRosterManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SREDisableManager {
    public static HarpyModLoaderConfig config = HarpyModLoaderConfig.instance();

    /** 当前地图级别的禁用职业集合（由地图配置的 bannedRoles 字段设置）。游戏结束时清空。 */
    private static final Set<String> mapBannedRoles = new HashSet<>();

    public static void setMapBannedRoles(Set<String> roles) {
        mapBannedRoles.clear();
        if (roles != null) {
            mapBannedRoles.addAll(roles);
        }
    }

    public static void clearMapBannedRoles() {
        mapBannedRoles.clear();
    }

    public static Set<String> getMapBannedRoles() {
        return Collections.unmodifiableSet(mapBannedRoles);
    }

    public static boolean isRoleDisabled(SRERole role) {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            boolean onewayflag = false;

            if (!RoleManageConfigUI.RoleEnableStatus.isEmpty()) {
                if (!RoleManageConfigUI.RoleEnableStatus.getOrDefault(role.identifier().toString(), true))
                    return true;
                onewayflag = true;
            }
            if (ClientRoleRosterCache.snapshot().enabled) {
                if (ClientRoleRosterCache.snapshot().roleCounts.getOrDefault(role.identifier().toString(), 0) <= 0) {
                    return true;
                }
                onewayflag = true;
            }
            if (onewayflag) {
                return false;
            }
        }
        // 地图级别的职业禁用（优先级最高）
        if (!mapBannedRoles.isEmpty() && mapBannedRoles.contains(role.identifier().toString()))
            return true;
        // 优先采用本地 config
        if (config.disabled != null && config.disabled.contains(role.identifier().toString()))
            return true;
        if (!RoleRosterManager.isRoleEnabled(role))
            return true;
        return false;
    }

    public static boolean isModifierDisabled(SREModifier modifier) {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            boolean onewayflag = false;
            if (!RoleManageConfigUI.ModifierEnableStatus.isEmpty()) {
                if (!RoleManageConfigUI.ModifierEnableStatus.getOrDefault(modifier.identifier().toString(), true))
                    return true;
                onewayflag = true;
            }
            if (ClientRoleRosterCache.snapshot().enabled) {
                if (ClientRoleRosterCache.snapshot().modifierCounts.getOrDefault(modifier.identifier().toString(),
                        0) <= 0) {
                    return true;
                }
                onewayflag = true;
            }
            if (onewayflag) {
                return false;
            }
        }
        // 优先采用本地 config
        if (config.disabledModifiers != null && config.disabledModifiers.contains(modifier.identifier().toString()))
            return true;
        if (!RoleRosterManager.isModifierEnabled(modifier))
            return true;
        return false;
    }

    public static boolean isRoleRosterDisabled(SRERole role) {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (!ClientRoleRosterCache.snapshot().enabled)
                return false;

            if (ClientRoleRosterCache.snapshot().roleCounts.getOrDefault(role.identifier().toString(), 0) <= 0) {
                return true;
            }
            return false;
        }
        if (!RoleRosterManager.isRoleEnabled(role))
            return true;
        return false;
    }

    public static boolean isModifierRosterDisabled(SREModifier modifier) {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (!ClientRoleRosterCache.snapshot().enabled)
                return false;

            if (ClientRoleRosterCache.snapshot().modifierCounts.getOrDefault(modifier.identifier().toString(),
                    0) <= 0) {
                return true;
            }
            return false;
        }
        if (!RoleRosterManager.isModifierEnabled(modifier))
            return true;
        return false;
    }

    public static boolean isRoleConfigDisabled(SRERole role) {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (!RoleManageConfigUI.RoleEnableStatus.isEmpty()) {
                if (!RoleManageConfigUI.RoleEnableStatus.getOrDefault(role.identifier().toString(), true))
                    return true;
                return false;
            }
        }
        // 优先采用本地 config
        if (config.disabled != null && config.disabled.contains(role.identifier().toString()))
            return true;
        return false;
    }

    public static boolean isModifierConfigDisabled(SREModifier modifier) {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (!RoleManageConfigUI.ModifierEnableStatus.isEmpty()) {
                if (!RoleManageConfigUI.ModifierEnableStatus.getOrDefault(modifier.identifier().toString(), true))
                    return true;
                return false;
            }
        }
        // 优先采用本地 config
        if (config.disabledModifiers != null && config.disabledModifiers.contains(modifier.identifier().toString()))
            return true;
        return false;
    }
}
