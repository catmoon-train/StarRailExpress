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

import net.minecraft.client.gui.screens.Screen;

public interface ModuleContext {
    double ax();

    double ay();

    double az();

    float playerYaw();

    float playerPitch();

    void sendOnly(String cmd);

    void sendAndClose(String cmd);

    double getOffsetX();

    double getOffsetY();

    double getOffsetZ();

    void setOffsetX(double v);

    void setOffsetY(double v);

    void setOffsetZ(double v);

    void resetOffsets();

    void refreshScreen();

    String quoteCommandArgument(String value);

    /**
     * 请求仅刷新当前激活的模块（不重建整个屏幕）。
     * 实现应重新初始化该模块并更新可滚动控件，同时保留固定控件和滚动偏移。
     */
    void requestModuleRefresh();

    Screen screen();

    void registerCloseHook(Runnable runner);
}