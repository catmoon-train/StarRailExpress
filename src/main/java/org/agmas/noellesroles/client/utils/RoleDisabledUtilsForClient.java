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

package org.agmas.noellesroles.client.utils;

import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.SREModifier;

public class RoleDisabledUtilsForClient {
    public static boolean isModifierDisabled(SREModifier modifier) {
        if (modifier == null)
            return true;
        var mc = Minecraft.getInstance();
        var hpconfig = HarpyModLoaderConfig.HANDLER.instance();
        if (mc.level == null)
            return false;
        if (mc.isLocalServer() || mc.isSingleplayer()) {
            return hpconfig.getDisabledModifiers().contains(modifier.identifier().toString());
        }

        if (SREClient.gameComponent == null)
            return false;

        if (RoleManageConfigUI.ModifierEnableStatus.isEmpty())
            return false;
        return !RoleManageConfigUI.ModifierEnableStatus.getOrDefault(modifier.identifier().toString(), false);
    }

    public static boolean isRoleDisabled(SRERole role) {
        if (role == null)
            return true;
        var mc = Minecraft.getInstance();
        var hpconfig = HarpyModLoaderConfig.HANDLER.instance();
        if (mc.level == null || mc.isLocalServer() || mc.isSingleplayer()) {
            return hpconfig.getDisabled().contains(role.identifier().toString());
        }

        if (SREClient.gameComponent == null)
            return false;
        if (RoleManageConfigUI.RoleEnableStatus.isEmpty())
            return false;
        return !RoleManageConfigUI.RoleEnableStatus.getOrDefault(role.identifier().toString(), false);
    }
}
