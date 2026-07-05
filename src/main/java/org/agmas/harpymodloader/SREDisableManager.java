package org.agmas.harpymodloader;

import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.data.ClientRoleRosterCache;
import io.wifi.starrailexpress.roster.RoleRosterManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.SREModifier;

public class SREDisableManager {
    public static HarpyModLoaderConfig config = HarpyModLoaderConfig.instance();

    public static boolean isRoleDisabled(SRERole role) {
        if (role == null)
            return true;
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (SREClient.areaComponent != null) {
                if (SREClient.areaComponent.disabledRoles.contains(role.identifier().toString()))
                    return true;
            }
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
        if (SRE.SERVER != null) {
            var cca = AreasWorldComponent.KEY.get(SRE.SERVER.overworld());
            if (cca.disabledRoles.contains(role.identifier().toString())) {
                return true;
            }
        }
        // 优先采用本地 config
        if (config.disabled != null && config.disabled.contains(role.identifier().toString()))
            return true;
        if (!RoleRosterManager.isRoleEnabled(role))
            return true;
        return false;
    }

    public static boolean isModifierDisabled(SREModifier modifier) {
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (SREClient.areaComponent != null) {
                if (SREClient.areaComponent.disabledModifiers.contains(modifier.identifier().toString()))
                    return true;
            }
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
        
        if (SRE.SERVER != null) {
            var cca = AreasWorldComponent.KEY.get(SRE.SERVER.overworld());
            if (cca.disabledModifiers.contains(modifier.identifier().toString())) {
                return true;
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
