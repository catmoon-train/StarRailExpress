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

import java.util.List;

/**
 * 广播员技能界面：模拟按技能键打开的 {@code BroadcasterScreen}，以及消息发出后
 * 顶部居中的绿色广播条（游戏内由 {@code BroadcasterHud} 绘制）。
 *
 * <p>离屏沙盒里拿不到真实的 Screen 与服务端同步数据，因此按同一版式自绘：
 * 输入框 / 提示 / 花费行，再叠加顶部绿色消息条。
 */
public class PonderBroadcastOverlayElement implements PonderOverlayElement {

    private static final int PANEL_WIDTH = 324;
    private static final int PANEL_HEIGHT = 132;

    private final PonderLocalizedText title;
    private final List<PonderLocalizedText> lines;
    private final int cost;
    private boolean visible = true;

    public PonderBroadcastOverlayElement(PonderLocalizedText title, List<PonderLocalizedText> lines,
                                         int cost) {
        this.title = title == null || title.isEmpty()
                ? PonderLocalizedText.of("screen.noellesroles.broadcaster.title") : title;
        this.lines = List.copyOf(lines == null ? List.of() : lines);
        this.cost = cost;
    }

    /** 加入场景并停留 {@code ticks} 个 tick，与游戏内消息条持续时间一致。 */
    public PonderBroadcastOverlayElement addTo(SceneBuilder scene, int ticks) {
        scene.addInstruction(ps -> {
            ps.addElement(this);
            this.visible = true;
        });
        scene.idle(Math.max(1, ticks));
        scene.addInstruction(ps -> this.visible = false);
        return this;
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
        int x = (screen.width - PANEL_WIDTH) / 2;
        int y = (screen.height - PANEL_HEIGHT) / 2;

        // 广播编辑界面：深色底板 + 标题 + 输入框 + 提示。
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xC0101018);
        graphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3A3A4A);
        int textX = x + 12;
        int textY = y + 10;
        graphics.drawString(font, title.resolve(), textX, textY, 0xFF55FFFF, true);
        textY += font.lineHeight + 8;

        graphics.fill(textX, textY, textX + PANEL_WIDTH - 24, textY + 16, 0xFF000000);
        graphics.renderOutline(textX, textY, PANEL_WIDTH - 24, 16, 0xFF55556A);
        textY += 16 + 8;

        for (PonderLocalizedText line : lines) {
            graphics.drawString(font, line.resolve(), textX, textY, 0xFFEEEEEE, true);
            textY += font.lineHeight + 3;
        }
        if (cost >= 0) {
            graphics.drawString(font, Component.literal("-" + cost),
                    textX, textY, 0xFFFF5555, true);
        }

        // 顶部绿色广播条：与 BroadcasterHud 一致，正文居中且有半透明底。
        if (!lines.isEmpty()) {
            Component message = lines.get(0).resolve();
            int barWidth = Math.min(screen.width - 20, font.width(message) + 28);
            int barX = (screen.width - barWidth) / 2;
            int barY = 84;
            graphics.fill(barX, barY, barX + barWidth, barY + font.lineHeight + 10, 0x90000000);
            graphics.drawString(font, message,
                    barX + (barWidth - font.width(message)) / 2, barY + 5, 0xFF55FF55, true);
        }
    }
}
