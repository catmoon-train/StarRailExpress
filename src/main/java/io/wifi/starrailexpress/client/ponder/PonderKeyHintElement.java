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

package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 思索里的「按键提示」：屏幕下方居中画一个键帽，键帽下面是说明文字。
 *
 * <p>Ponder 自带的 {@code show_controls} 只能画鼠标左/右键与滚轮（外加 Ctrl / Shift 修饰），
 * 没有键盘按键的图形；而职业技能的触发全部是键盘按键，所以这里补一个键帽元素。
 *
 * <p>键帽显示的是**当前实际绑定**的按键名（玩家改键后自动跟着变），不是写死的字母。
 * {@code lifeTicks > 0} 时到点自动消失，{@code <= 0} 表示整段保持显示。
 */
public class PonderKeyHintElement implements PonderOverlayElement {

    /** 键帽距屏幕底部的像素高度，避开 Ponder 的进度条与播放按钮。 */
    private static final int BOTTOM_INSET = 96;

    private final String keyName;
    private final String caption;
    private final int accent;
    private final int lifeTicks;

    private int remaining;
    private boolean visible;

    public PonderKeyHintElement(String keyName, String caption, int accent, int lifeTicks) {
        this.keyName = keyName == null ? "" : keyName;
        this.caption = caption == null ? "" : caption;
        this.accent = accent;
        this.lifeTicks = lifeTicks;
    }

    public PonderKeyHintElement addTo(SceneBuilder scene) {
        scene.addInstruction(ps -> {
            ps.addElement(this);
            this.visible = true;
            this.remaining = this.lifeTicks;
        });
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
    public void reset(PonderScene scene) {
        // 重播时先熄灭，再由 addTo 的指令重新点亮，这样第二遍的时间点与第一遍一致。
        this.visible = false;
        this.remaining = this.lifeTicks;
    }

    @Override
    public void tick(PonderScene scene) {
        if (!visible || lifeTicks <= 0) {
            return;
        }
        if (--remaining <= 0) {
            visible = false;
        }
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        if (!visible) {
            return;
        }
        Font font = Minecraft.getInstance().font;

        int keyWidth = Math.max(20, font.width(keyName) + 12);
        int keyHeight = 18;
        int centerX = screen.width / 2;
        int keyX = centerX - keyWidth / 2;
        int keyY = screen.height - BOTTOM_INSET;

        graphics.fill(keyX, keyY, keyX + keyWidth, keyY + keyHeight, 0xE0141418);
        graphics.renderOutline(keyX, keyY, keyWidth, keyHeight, accent);
        graphics.drawString(font, keyName, keyX + (keyWidth - font.width(keyName)) / 2,
                keyY + (keyHeight - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);

        if (!caption.isEmpty()) {
            graphics.drawString(font, caption, centerX - font.width(caption) / 2,
                    keyY + keyHeight + 6, 0xFFEEEEEE, false);
        }
    }
}
