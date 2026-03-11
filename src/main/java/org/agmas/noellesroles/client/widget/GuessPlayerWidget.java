package org.agmas.noellesroles.client.widget;


import org.agmas.noellesroles.client.screen.GuessRoleScreen;
import org.jetbrains.annotations.NotNull;
import net.minecraft.client.multiplayer.PlayerInfo;
import java.awt.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * 猜测身份玩家选择 Widget
 * 
 * 显示玩家头像和猜测的职业
 */
public class GuessPlayerWidget extends Button {

    public final GuessRoleScreen screen;
    public final PlayerInfo player;
    private final int size;
    private final String guessedRole;

    public GuessPlayerWidget(GuessRoleScreen screen, int x, int y, int size,
                             @NotNull PlayerInfo player, String guessedRole) {
        super(x, y, size, size + 15, Component.nullToEmpty(player.getProfile().getName()), // 高度增加以容纳文字
                (button) -> screen.onPlayerSelected(player.getProfile().getId(), player.getProfile().getName()),
                DEFAULT_NARRATION);
        this.screen = screen;
        this.player = player;
        this.size = size;
        this.guessedRole = guessedRole != null ? guessedRole : "???";
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 绘制背景框
        int bgColor = this.isHovered() ? new Color(100, 50, 150, 200).getRGB() : new Color(50, 25, 75, 150).getRGB();
        context.fill(getX() - 2, getY() - 2, getX() + size + 2, getY() + size + 15, bgColor);

        // 绘制边框
        int borderColor = this.isHovered() ? new Color(150, 100, 200).getRGB() : new Color(75, 50, 100).getRGB();
        context.renderOutline(getX() - 2, getY() - 2, size + 4, size + 17, borderColor);

        // 绘制玩家皮肤头像
        PlayerFaceRenderer.draw(context, player.getSkin(), getX(), getY(), size);

        // 绘制猜测的职业名称
        Font font = Minecraft.getInstance().font;
        Component roleText = Component.literal(guessedRole);
        int textWidth = font.width(roleText);
        int textX = getX() + (size - textWidth) / 2;
        int textY = getY() + size + 4;
        context.drawString(font, roleText, textX, textY, 0xFFFFFF, true);

        // 高亮效果
        if (this.isHovered()) {
            drawShopSlotHighlight(context, getX(), getY(), 0);

            // 绘制玩家名称提示
            int nameWidth = font.width(player.getProfile().getName());
            context.renderTooltip(font, Component.nullToEmpty(player.getProfile().getName()),
                    getX() - 12 + (size - nameWidth) / 2, getY() - 2);
        }
    }

    private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
        int color = new Color(150, 100, 200, 100).getRGB();
        context.fillGradient(RenderType.guiOverlay(), x, y, x + size, y + size, color, color, z);
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 不绘制默认消息
    }
}