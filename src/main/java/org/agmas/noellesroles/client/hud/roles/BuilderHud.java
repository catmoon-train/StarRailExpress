package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.Innocent.builder.BuilderPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 建筑师 HUD
 * 右下角显示当前模式和冷却时间
 */
public class BuilderHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.BUILDER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            BuilderPlayerComponent builderComponent = BuilderPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 150;
            int y = screenHeight - 30;

            Font textRenderer = client.font;

            // 显示当前模式
            Component modeText = builderComponent.isBuildMode() ?
                Component.translatable("hud.noellesroles.builder.mode.build") :
                Component.translatable("hud.noellesroles.builder.mode.demolish");
            int modeColor = builderComponent.isBuildMode() ? CommonColors.GREEN : 0xFFAA00; // 绿色=建造, 橙色=拆除
            context.drawString(textRenderer, modeText, x, y, modeColor);

            // 显示冷却时间
            if (builderComponent.isBuildMode() && builderComponent.cooldown > 0) {
                float cdSeconds = builderComponent.getCooldownSeconds();
                Component cdText = Component.translatable("hud.noellesroles.builder.cooldown",
                    String.format("%.1f", cdSeconds));
                context.drawString(textRenderer, cdText, x, y + 12, CommonColors.RED);
            } else if (builderComponent.isBuildMode()) {
                Component readyText = Component.translatable("hud.noellesroles.builder.ready");
                context.drawString(textRenderer, readyText, x, y + 12, CommonColors.GREEN);
            }
        });
    }
}
