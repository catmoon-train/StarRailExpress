package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vigilante.SwordsmanRoleData;

/**
 * 剑客 HUD：显示「淬血」的剩余持续时间。
 *
 * <p>淬血未生效时不画任何东西（冷却显示交给 {@code showOnHud(true)} 的统一技能 HUD）。
 */
public final class SwordsmanHud {

    private SwordsmanHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SWORDSMAN_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()
                    || AdvancedCameraDirector.shouldOverride()) {
                return;
            }
            SwordsmanRoleData data = RoleData.getNullable(SwordsmanRoleData.class, client.player);
            if (data == null || data.quXueLeftTicks() <= 0) {
                return;
            }
            // 向上取整到秒，避免 14.9 秒显示成 14
            int seconds = (data.quXueLeftTicks() + 19) / 20;
            Component text = Component.translatable("hud.noellesroles.swordsman.quxue", seconds);
            int x = context.guiWidth() / 2 - client.font.width(text) / 2;
            int y = context.guiHeight() - 60;
            context.drawString(client.font, text, x, y, 0xFFFF6B6B);
        });
    }
}
