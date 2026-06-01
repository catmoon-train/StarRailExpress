package org.agmas.noellesroles.client.utils;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.SREModifier;

import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;

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
