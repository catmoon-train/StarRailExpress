/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 自定义内容编辑器里的说明文字绘制：**放不下就换行，换不下就截断并给出悬停全文**。
 *
 * <p>
 * 原来的四个编辑器都是「一行 + 硬截断」，长一点的说明（尤其是整段的 hint）会被切掉，
 * 有的界面还会因为截断宽度给得过大而把文字画到输入框上面。这里统一成：
 * <ul>
 * <li>{@code maxLines == 1} 的（字段旁边的标签）：单行画，超宽用省略号截断，悬停看全文；</li>
 * <li>{@code maxLines >= 2} 的（整段提示）：按行位宽度自动换行，最多画 {@code maxLines} 行，
 * 仍然放不下的部分靠悬停 tooltip 补全。</li>
 * </ul>
 *
 * <p>
 * tooltip 必须画在调用方 {@code disableScissor()} 之后，所以 {@link #draw} 只返回
 * 「需要补一个悬停全文」的那一条，由调用方再调 {@link #drawTooltip}。
 */
@Environment(EnvType.CLIENT)
public final class HintText {

    /** 行距（字体行高 9，留 1px 余量）。 */
    public static final int LINE_H = 10;
    /** tooltip 自动换行的宽度。 */
    public static final int TOOLTIP_W = 260;

    /** 一条待绘制的说明文字。 */
    public record Line(Component text, int x, int y, int color, int maxWidth, int maxLines) {
    }

    private HintText() {
    }

    /**
     * 画出一批说明文字。
     *
     * @return 被截断且鼠标正悬停其上的那一条（没有则 null），调用方在关闭裁剪后用它画 tooltip
     */
    public static Line draw(GuiGraphics g, Font font, List<Line> lines, int mouseX, int mouseY) {
        Line hovered = null;
        for (Line line : lines) {
            int width = Math.max(20, line.maxWidth());
            int maxLines = Math.max(1, line.maxLines());
            if (maxLines == 1) {
                String full = line.text().getString();
                String shown = MapUiGraphics.clip(font, full, width);
                g.drawString(font, Component.literal(shown), line.x(), line.y(), line.color(), false);
                if (!shown.equals(full) && MapUiGraphics.isInRect(mouseX, mouseY, line.x(), line.y(), width, LINE_H)) {
                    hovered = line;
                }
                continue;
            }
            List<FormattedCharSequence> wrapped = font.split(line.text(), width);
            int drawn = Math.min(wrapped.size(), maxLines);
            for (int i = 0; i < drawn; i++) {
                g.drawString(font, wrapped.get(i), line.x(), line.y() + i * LINE_H, line.color(), false);
            }
            if (wrapped.size() > maxLines
                    && MapUiGraphics.isInRect(mouseX, mouseY, line.x(), line.y(), width, drawn * LINE_H)) {
                hovered = line;
            }
        }
        return hovered;
    }

    /** 画悬停全文（调用方已关闭 scissor）。 */
    public static void drawTooltip(GuiGraphics g, Font font, Line line, int mouseX, int mouseY) {
        g.renderTooltip(font, font.split(line.text(), TOOLTIP_W), mouseX, mouseY);
    }
}
