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

package io.wifi.starrailexpress.client.gui.screen.map_dev;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import java.util.List;

public interface TabModule {
    Component getTabTitle();

    /** 页签全名（会显示在面板标题条里；默认与页签名相同）。 */
    default Component getTabFullTitle() {
        return getTabTitle();
    }

    void init(LayoutContext layout, ModuleContext context, List<WidgetPlacement> placements);

    int getContentHeight();

    /**
     * 需要在<b>页签栏下面</b>留一条常驻控件带时覆写（例如「全部设置」的搜索框）。
     *
     * <p>
     * 只报高度、不碰坐标：界面会先按这个高度排页签与内容区（内容区整体下移这一段，
     * 裁剪 / 滚动 / 滚动条槽都跟着让开），再回调 {@link #buildTopStrip} 让你把控件放进去 ——
     * 见 {@link LayoutContext#stripTop} 与 {@link LayoutContext#stripBottom}。
     */
    default int topStripHeight() {
        return 0;
    }

    /**
     * 往页签下方的常驻带里放固定控件（贴在页签按钮下面、不随内容滚动）。
     *
     * <p>
     * 只有 {@link #topStripHeight()} 返回非 0 时才会被调用；控件请放进 {@code fixed} 列表。
     */
    default void buildTopStrip(LayoutContext layout, ModuleContext context, List<AbstractWidget> fixed) {
    }

    default void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * 内容区控件<b>之前</b>画一层（垫在控件下面的东西：分类卡片底、分组底色这类）。
     *
     * <p>
     * 调用时屏幕已经开好内容区裁剪，坐标请减去 {@code scrollOffset} 换算到当前视口；
     * 画在这里的东西不会被控件盖住，也不会盖住控件。
     */
    default void renderContentBackground(GuiGraphics g, int scrollOffset) {
    }
}