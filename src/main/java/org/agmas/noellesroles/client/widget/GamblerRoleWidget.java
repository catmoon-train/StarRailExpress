package org.agmas.noellesroles.client.widget;

import org.agmas.noellesroles.client.screen.GamblerScreen;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.Role;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * 阴谋家角色选择 Widget
 * 
 * 显示角色名称和颜色，点击选择该角色进行猜测
 */
public class GamblerRoleWidget extends Button {

    public final GamblerScreen screen;
    public final Role role;
    private final int buttonWidth;
    private final int buttonHeight;

    public GamblerRoleWidget(GamblerScreen screen, int x, int y, int width, int height,
            @NotNull Role role, int index) {
        super(x, y, width, height, RoleUtils.getRoleName(role),
                (button) -> screen.onRoleSelected(role),
                DEFAULT_NARRATION);
        this.screen = screen;
        this.role = role;
        this.buttonWidth = width;
        this.buttonHeight = height;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Font textRenderer = Minecraft.getInstance().font;

        // 获取角色颜色
        Color roleColor = new Color(role.color());

        // 背景颜色 - 使用角色颜色的暗化版本
        int bgAlpha = this.isHovered() ? 200 : 150;
        Color bgColor = new Color(
                Math.max(0, roleColor.getRed() / 3),
                Math.max(0, roleColor.getGreen() / 3),
                Math.max(0, roleColor.getBlue() / 3),
                bgAlpha);

        // 绘制背景
        context.fill(getX(), getY(), getX() + buttonWidth, getY() + buttonHeight, bgColor.getRGB());

        // 边框颜色 - 使用角色颜色
        Color borderColor = this.isHovered()
                ? new Color(
                        Math.min(255, roleColor.getRed() + 50),
                        Math.min(255, roleColor.getGreen() + 50),
                        Math.min(255, roleColor.getBlue() + 50))
                : roleColor;

        // 绘制边框
        context.renderOutline(getX(), getY(), buttonWidth, buttonHeight, borderColor.getRGB());

        // 绘制角色名称
        Component roleName = RoleUtils.getRoleName(role);
        int textWidth = textRenderer.width(roleName);
        int textX = getX() + (buttonWidth - textWidth) / 2;
        int textY = getY() + (buttonHeight - 8) / 2;

        // 使用角色颜色绘制文字
        context.drawString(textRenderer, roleName, textX, textY, role.color());

        // 高亮效果
        if (this.isHovered()) {
            drawSlotHighlight(context, getX(), getY());
        }
    }

    private void drawSlotHighlight(GuiGraphics context, int x, int y) {
        Color roleColor = new Color(role.color());
        int color = new Color(roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue(), 80).getRGB();
        context.fillGradient(RenderType.guiOverlay(), x, y, x + buttonWidth, y + buttonHeight, color, color, 0);
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 不使用默认消息绘制
    }
}