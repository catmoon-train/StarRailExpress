/* GNU General Public License v3 or later */
package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 电报员技能界面：模拟 {@code TelegrapherScreen} 的输入页，以及发送后
 * 显示在全体玩家屏幕上的淡蓝色标题。
 *
 * <p>离屏沙盒里没有真实 Screen 与服务端旁白包，按同一版式自绘：
 * 青色标题、剩余次数、输入框、确认按钮，再切到顶部淡蓝色消息条。
 */
public class PonderTelegraphOverlayElement implements PonderOverlayElement {

    private static final int PANEL_WIDTH = 320;

    private final PonderLocalizedText title;
    private final PonderLocalizedText hint;
    private final PonderLocalizedText message;
    private final int remaining;
    private boolean visible = true;
    private boolean sent = false;

    public PonderTelegraphOverlayElement(PonderLocalizedText title, PonderLocalizedText hint,
                                         PonderLocalizedText message, int remaining) {
        this.title = title == null || title.isEmpty()
                ? PonderLocalizedText.of("Telegraph") : title;
        this.hint = hint == null ? PonderLocalizedText.of("") : hint;
        this.message = message == null ? PonderLocalizedText.of("") : message;
        this.remaining = remaining;
    }

    /** 加入场景：前半段是编辑页，后半段是发出后的标题条。 */
    public void addTo(SceneBuilder scene, int ticks) {
        int total = Math.max(2, ticks);
        scene.addInstruction(ps -> {
            ps.addElement(this);
            this.visible = true;
            this.sent = false;
        });
        scene.idle(total / 2);
        scene.addInstruction(ps -> this.sent = true);
        scene.idle(total - total / 2);
        scene.addInstruction(ps -> this.visible = false);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        if (!visible) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int centerX = screen.width / 2;
        if (sent) {
            Component body = message.resolve();
            int barWidth = Math.min(screen.width - 20, font.width(body) + 36);
            int barX = centerX - barWidth / 2;
            graphics.fill(barX, 36, barX + barWidth, 36 + font.lineHeight + 14, 0x900B1C3A);
            graphics.renderOutline(barX, 36, barWidth, font.lineHeight + 14, 0xFF55AAFF);
            graphics.drawString(font, body, barX + 18, 43, 0xFF99DDFF, true);
            return;
        }

        int centerY = screen.height / 2;
        graphics.drawCenteredString(font, title.resolve(), centerX, centerY - 62, 0xFF55FFFF);
        Component uses = Component.translatable("screen.noellesroles.telegrapher.remaining", remaining);
        graphics.drawCenteredString(font, uses, centerX, centerY - 44,
                remaining > 0 ? 0xFF55FF55 : 0xFFFF5555);
        graphics.drawCenteredString(font, hint.resolve(), centerX, centerY - 26, 0xFF888888);

        int fieldX = centerX - PANEL_WIDTH / 2;
        int fieldY = centerY - 8;
        graphics.fill(fieldX, fieldY, fieldX + PANEL_WIDTH, fieldY + 20, 0xFF000000);
        graphics.renderOutline(fieldX, fieldY, PANEL_WIDTH, 20, 0xFF55556A);
        graphics.drawString(font, message.resolve(), fieldX + 4, fieldY + 4, 0xFFEEEEEE, true);

        int buttonWidth = 100;
        graphics.fill(centerX - buttonWidth / 2, fieldY + 30,
                centerX + buttonWidth / 2, fieldY + 50, 0xFF2A2A3A);
        graphics.renderOutline(centerX - buttonWidth / 2, fieldY + 30, buttonWidth, 20, 0xFF777799);
        graphics.drawCenteredString(font,
                Component.translatable("screen.noellesroles.telegrapher.confirm"),
                centerX, fieldY + 35, 0xFFFFFFFF);
    }
}
