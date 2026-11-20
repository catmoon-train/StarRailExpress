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
import net.minecraft.util.Mth;
import java.util.List;

/**
 * 思索里的通用状态面板：虚拟血量/护盾条、时间进度、金币、状态效果、
 * 绑定关系与文书内容。画在左上角，与右上角的 {@link PonderInfoOverlayElement} 不冲突。
 */
public class PonderStateOverlayElement implements PonderOverlayElement {

    /** 一条带变化前后对比的数值条。 */
    public record Bar(String label, float before, float after, int color) {
    }

    private static final int BAR_WIDTH = 110;

    private final String title;
    private final List<Bar> bars;
    private final Float progressBefore;
    private final Float progressAfter;
    private final List<String> lines;
    private final int accent;
    private boolean visible = true;

    public PonderStateOverlayElement(String title, List<Bar> bars, Float progressBefore,
                                     Float progressAfter, List<String> lines, int accent) {
        this.title = title == null ? "" : title;
        this.bars = List.copyOf(bars == null ? List.of() : bars);
        this.progressBefore = progressBefore;
        this.progressAfter = progressAfter;
        this.lines = List.copyOf(lines == null ? List.of() : lines);
        this.accent = accent;
    }

    public PonderStateOverlayElement addTo(SceneBuilder scene) {
        scene.addInstruction(ps -> { ps.addElement(this); this.visible = true; });
        return this;
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        if (!visible) return;
        Font font = Minecraft.getInstance().font;
        int width = Math.max(150, font.width(title) + 24);
        for (Bar bar : bars) width = Math.max(width, font.width(bar.label()) + BAR_WIDTH + 34);
        for (String line : lines) width = Math.max(width, font.width(line) + 24);
        width = Math.min(width, Math.max(150, screen.width / 2 - 24));

        int x = 16;
        int y = 24;
        int rows = 1 + bars.size() * 2 + lines.size();
        if (progressBefore != null || progressAfter != null) rows += 2;
        int height = 14 + rows * (font.lineHeight + 2);
        graphics.fill(x, y, x + width, y + height, 0xC0101018);
        graphics.renderOutline(x, y, width, height, 0xFF3A3A4A);

        int textX = x + 10;
        int textY = y + 7;
        if (!title.isEmpty()) {
            graphics.drawString(font, Component.literal(title), textX, textY, accent);
            textY += font.lineHeight + 5;
        }

        for (Bar bar : bars) {
            graphics.drawString(font, Component.literal(bar.label()), textX, textY, 0xFFDDDDDD);
            textY += font.lineHeight;
            int barX = textX + 2;
            graphics.fill(barX, textY, barX + BAR_WIDTH, textY + 8, 0xFF202028);
            graphics.renderOutline(barX, textY, BAR_WIDTH, 8, 0xFF55556A);
            graphics.fill(barX + 1, textY + 1,
                    barX + 1 + Math.round((BAR_WIDTH - 2) * Mth.clamp(bar.before(), 0, 1)),
                    textY + 7, 0x80AAAAAA);
            graphics.fill(barX + 1, textY + 1,
                    barX + 1 + Math.round((BAR_WIDTH - 2) * Mth.clamp(bar.after(), 0, 1)),
                    textY + 7, bar.color());
            textY += 10;
        }

        if (progressBefore != null || progressAfter != null) {
            float before = progressBefore == null ? 0.0F : progressBefore;
            float after = progressAfter == null ? before : progressAfter;
            int barX = textX + 2;
            graphics.fill(barX, textY, barX + BAR_WIDTH, textY + 8, 0xFF202028);
            graphics.renderOutline(barX, textY, BAR_WIDTH, 8, 0xFF55556A);
            graphics.fill(barX + 1, textY + 1,
                    barX + 1 + Math.round((BAR_WIDTH - 2) * Mth.clamp(before, 0, 1)),
                    textY + 7, 0x80AAAAAA);
            graphics.fill(barX + 1, textY + 1,
                    barX + 1 + Math.round((BAR_WIDTH - 2) * Mth.clamp(after, 0, 1)),
                    textY + 7, accent);
            textY += 13;
        }

        for (String line : lines) {
            graphics.drawString(font, Component.literal(line), textX, textY, 0xFFEEEEEE);
            textY += font.lineHeight + 3;
        }
    }
}
