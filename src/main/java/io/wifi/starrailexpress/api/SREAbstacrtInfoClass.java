package io.wifi.starrailexpress.api;

import org.agmas.noellesroles.utils.RoleUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class SREAbstacrtInfoClass {
    public abstract ResourceLocation getIdentifier();

    public Component getDisplayName() {
        return RoleUtils.getRoleOrModifierName(this);
    }

    public Component getDisplayNameWithColor() {
        return RoleUtils.getRoleOrModifierNameWithColor(this);
    }
}
