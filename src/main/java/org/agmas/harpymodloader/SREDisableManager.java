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
import org.agmas.noellesroles.client.utils.RoleDisabledUtilsForClient;

import java.util.Set;

public class SREDisableManager {
    public static HarpyModLoaderConfig config = HarpyModLoaderConfig.instance();

    /** 地图禁用的职业 ID；组件或设置缺失时返回空集合。 */
    private static Set<String> areaDisabledRoles(AreasWorldComponent areas) {
        if (areas == null || areas.areasSettings == null || areas.areasSettings.disabledRoles == null)
            return Set.of();
        return areas.areasSettings.disabledRoles;
    }

    /** 地图禁用的修饰符 ID；组件或设置缺失时返回空集合。 */
    private static Set<String> areaDisabledModifiers(AreasWorldComponent areas) {
        if (areas == null || areas.areasSettings == null || areas.areasSettings.disabledModifiers == null)
            return Set.of();
        return areas.areasSettings.disabledModifiers;
    }

    public static boolean isRoleDisabled(SRERole role) {
        if (role == null)
            return true;
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (SREClient.areaComponent != null) {
                if (areaDisabledRoles(SREClient.areaComponent).contains(role.identifier().toString()))
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
            
            
            if (RoleDisabledUtilsForClient.isInMultiplayerGame()) {
                return false;
            }
        }
        if (SRE.SERVER != null) {
            var cca = AreasWorldComponent.KEY.get(SRE.SERVER.overworld());
            if (areaDisabledRoles(cca).contains(role.identifier().toString())) {
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
                if (areaDisabledModifiers(SREClient.areaComponent).contains(modifier.identifier().toString()))
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
            
            if (RoleDisabledUtilsForClient.isInMultiplayerGame()) {
                return false;
            }
        }

        if (SRE.SERVER != null) {
            var cca = AreasWorldComponent.KEY.get(SRE.SERVER.overworld());
            if (areaDisabledModifiers(cca).contains(modifier.identifier().toString())) {
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
