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
 * 所以长译文不会溢出到相邻页签上；活跃页签金色粗体 + 底部金线 + 淡色底，hover 有过渡（文档 §6）。
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

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (selected) {
            hoverAnim = 1F;
        } else {
            hoverAnim += ((isHovered() ? 1F : 0F) - hoverAnim) * 0.22F;
        }
        int alpha = (int) (0x33 * hoverAnim) << 24;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                (alpha & 0xFF000000) | (SREPanelStyle.GOLD & 0x00FFFFFF));
        g.fill(getX(), getY(), getX() + 1, getY() + getHeight(),
                selected ? SREPanelStyle.GOLD : SREPanelStyle.CARD_BORDER);
        g.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(),
                selected ? SREPanelStyle.GOLD : SREPanelStyle.CARD_BORDER);
        if (selected) {
            g.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight(),
                    SREPanelStyle.GOLD);
        }

        int color = selected ? SREPanelStyle.GOLD : (isHovered() ? SREPanelStyle.TEXT : SREPanelStyle.MUTED);
        String shown = MapUiGraphics.clip(font, getMessage().getString(), Math.max(8, getWidth() - 8));
        g.drawCenteredString(font, Component.literal(shown), getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2, color);
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
