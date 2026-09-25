package org.agmas.noellesroles.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.function.Consumer;

/**
 * 独裁者界面通用「玩家头像」按钮：显示头像，点击回调选中的玩家。
 */
public class DictatorAvatarWidget extends Button {

    public final AbstractClientPlayer player;
    private final int size;
    private final Consumer<AbstractClientPlayer> onPick;

    public DictatorAvatarWidget(int x, int y, int size, @NotNull AbstractClientPlayer player,
            Consumer<AbstractClientPlayer> onPick) {
        super(x, y, size, size, player.getName(), button -> onPick.accept(player), DEFAULT_NARRATION);
        this.player = player;
        this.size = size;
        this.onPick = onPick;
    }

    @Override
    public void onPress() {
        onPick.accept(player);
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Color base = new Color(30, 60, 120);
        int bg = isHovered() ? new Color(60, 110, 200, 210).getRGB() : new Color(base.getRed(), base.getGreen(),
                base.getBlue(), 170).getRGB();
        int border = isHovered() ? new Color(120, 180, 255).getRGB() : new Color(50, 90, 160).getRGB();

        context.fill(getX() - 2, getY() - 2, getX() + size + 2, getY() + size + 2, bg);
        context.renderOutline(getX() - 2, getY() - 2, size + 4, size + 4, border);
        PlayerFaceRenderer.draw(context, player.getSkin().texture(), getX(), getY(), size);

        if (isHovered()) {
            int color = new Color(120, 180, 255, 90).getRGB();
            context.fillGradient(RenderType.guiOverlay(), getX(), getY(), getX() + size, getY() + size, color, color, 0);
            Font font = Minecraft.getInstance().font;
            int textWidth = font.width(player.getName());
            context.renderTooltip(font, player.getName(), getX() + size / 2 - textWidth / 2, getY() - 12);
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 不绘制默认文字（头像上方悬浮名字即可）
    }
}
