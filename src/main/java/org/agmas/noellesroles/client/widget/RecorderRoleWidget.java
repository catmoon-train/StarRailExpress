package org.agmas.noellesroles.client.widget;

import org.agmas.noellesroles.client.screen.RecorderScreen;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import java.awt.*;

public class RecorderRoleWidget extends Button {

    public final RecorderScreen screen;
    public final SRERole role;
    private final int buttonWidth;
    private final int buttonHeight;

    public RecorderRoleWidget(RecorderScreen screen, int x, int y, int width, int height,
            SRERole role, int index) {
        super(x, y, width, height, getRoleName(role),
                (button) -> screen.onRoleSelected(role),
                DEFAULT_NARRATION);
        this.screen = screen;
        this.role = role;
        this.buttonWidth = width;
        this.buttonHeight = height;
    }

    private static Component getRoleName(SRERole role) {
        return RoleUtils.getRoleName(role);
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
        Component roleName = getRoleName(role);
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