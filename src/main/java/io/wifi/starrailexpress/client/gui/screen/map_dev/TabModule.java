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
     * 需要额外头部高度时覆写（例如「全部设置」的搜索框占一行）。
     *
     * <p>
     * 只报高度、不碰坐标：界面会先按这个高度把头部与页签排好，再回调
     * {@link #buildHeader} 让你把控件放到头部区域里（见 {@link EditorLayout#headerTop()} 所在的那一段）。
     */
    default int headerExtraHeight() {
        return 0;
    }

    /**
     * 往头部区域放固定控件（在页签栏上方、不随内容滚动）。
     *
     * <p>
     * 只有 {@link #headerExtraHeight()} 返回非 0 时才会被调用；控件请放进 {@code fixed} 列表。
     */
    default void buildHeader(LayoutContext layout, ModuleContext context, List<AbstractWidget> fixed) {
    }

    default void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }
}