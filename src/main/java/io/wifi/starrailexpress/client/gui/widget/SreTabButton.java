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

package io.wifi.starrailexpress.client.gui.widget;

import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiSurface;
import io.wifi.starrailexpress.client.ui.UiText;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.EditorLayout;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * SRE 的页签按钮 —— 四个自定义工具编辑器与地图工具共用这一份实现（外观完全一致）。
 *
 * <p>
 * 宽度由调用方按文字实测排好（放不下时 {@link EditorLayout} 会折行），文字这里再裁一次省略号，
 * 宽度由调用方按文字实测排好（放不下时 {@link EditorLayout} 会折行），文字这里再裁一次省略号，
 * 所以长译文不会溢出到相邻页签上；活跃页签 = 黄铜描边 + 底部黄铜基线 + 高亮黄铜文字，hover 有过渡（文档 §6）。
 */
@Environment(EnvType.CLIENT)
public class SreTabButton extends AbstractWidget {

    private final Font font;
    private final boolean selected;
    private final Runnable onPress;
    private float hoverAnim;

    public SreTabButton(Font font, int x, int y, int w, Component message, boolean selected, Runnable onPress) {
        super(x, y, w, EditorLayout.TAB_H, message);
        this.font = font;
        this.selected = selected;
        this.onPress = onPress;
        // 页签文字放不下时（长译文 / 极窄面板）悬停看全文
        if (font.width(message) > Math.max(8, w - 8)) {
            setTooltip(Tooltip.create(message));
        }
    }

    /**
     * 页签（扁平）：活跃 = 深炭灰底 + 黄铜描边 + 底部 2px 黄铜基线；hover = 黄铜 14% 底。
     *
     * <p>
     * 底色一律走 {@link UiSurface#card}，不再自己 {@code fill} 一堆半透明块 —— 页签与卡片
     * 圆角、描边粗细从此是同一套。
     */
    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (selected) {
            hoverAnim = 1F;
        } else {
            hoverAnim += ((isHovered() ? 1F : 0F) - hoverAnim) * 0.22F;
        }
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        if (selected) {
            UiSurface.card(g, x, y, w, h, SRETheme.SURFACE_BOTTOM, SRETheme.BRASS, UiSurface.RADIUS_ROW);
            // 活跃页签的「基线」：底部 2px 黄铜，扁平但有指示性
            g.fill(x + 1, y + h - 2, x + w - 1, y + h, SRETheme.BRASS);
        } else if (hoverAnim > 0.01F) {
            int alpha = (int) (((SRETheme.ROW_HOVER >>> 24) & 0xFF) * hoverAnim);
            UiSurface.card(g, x, y, w, h, SRETheme.withAlpha(SRETheme.ROW_HOVER, alpha),
                    SRETheme.withAlpha(SRETheme.CARD_BORDER, (int) (0xFF * hoverAnim)), UiSurface.RADIUS_ROW);
        }

        int color = selected ? SRETheme.BRASS_BRIGHT : (isHovered() ? SRETheme.TEXT : SRETheme.MUTED);
        String shown = MapUiGraphics.clip(font, getMessage().getString(), Math.max(8, w - 8));
        UiText.drawCentered(g, font, Component.literal(shown), x + w / 2, y + (h - 8) / 2, color);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
