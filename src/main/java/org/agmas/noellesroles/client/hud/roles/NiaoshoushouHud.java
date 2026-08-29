/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.content.entity.NiaoshoushouMissileEntity;
import org.agmas.noellesroles.role.ModRoles;

/** 巡飞弹操控提示 HUD。 */
public final class NiaoshoushouHud {
    private NiaoshoushouHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.NIAOSHOU_SHOU_ID, (graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (!(client.getCameraEntity() instanceof NiaoshoushouMissileEntity)) {
                return;
            }

            int width = client.getWindow().getGuiScaledWidth();
            graphics.drawCenteredString(client.font,
                    Component.translatable("hud.noellesroles.niaoshoushou.missile"), width / 2, 18,
                    0xFFFF8A45);
            graphics.drawCenteredString(client.font,
                    Component.translatable("hud.noellesroles.niaoshoushou.controls"), width / 2, 31,
                    0xFFFFFFFF);
        });
    }
}
