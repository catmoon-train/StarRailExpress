package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 宿命的罪人 HUD：显示不同死因的累积进度。技能冷却由通用 UnifiedSkillHud 显示。
 */
public final class DoomedSinnerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.DOOMED_SINNER_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            DoomedSinnerPlayerComponent component = DoomedSinnerPlayerComponent.KEY.get(player);
            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 40;

            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.doomed_sinner.progress",
                            component.getDistinctCount(), component.requiredReasons),
                    x, y, 0xC07ED8);
        });
    }
}
