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

package org.agmas.noellesroles.client.rolescreen;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.PlayerPaginationHelper;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

/**
 * 背包界面"选人列表"扩展基类：持有 {@link RoleScreenHelper}，在 init/render 时驱动，
 * 默认附带玩家名搜索框 + 按名排序（轮椅方法）。
 *
 * <p>各职业扩展通过 {@link io.wifi.starrailexpress.api.SRERole} 的
 * {@code setInventoryScreenInitHandler} / {@code setInventoryScreenRenderHandler}
 * 在客户端注册，见 {@code RoleScreenRegister}。
 */
public abstract class PlayerListRoleScreenExtension<T> {

    protected static final PlayerPaginationHelper.PaginationTextProvider TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
        @Override
        public String getPageTranslationKey() {
            return "hud.pagination.page";
        }

        @Override
        public String getPrevTranslationKey() {
            return "hud.pagination.prev";
        }

        @Override
        public String getNextTranslationKey() {
            return "hud.pagination.next";
        }
    };

    protected RoleScreenHelper<T> helper;

    /** 创建本职业的 RoleScreenHelper（首次惰性创建）。 */
    protected abstract RoleScreenHelper<T> createHelper(LocalPlayer player);

    protected RoleScreenHelper<T> getHelper(LocalPlayer player) {
        if (helper == null) {
            helper = createHelper(player);
        }
        return helper;
    }

    /** 背包界面 {@code init()} 开头调用：填充选人列表并挂载玩家名搜索框。 */
    public void onInit(LimitedInventoryScreen screen) {
        RoleScreenHelper<T> h = getHelper(screen.player);
        h.onInit(screen);
        h.attachSearchBox(screen);
    }

    /** 背包界面 {@code render()} 开头调用（每帧）。 */
    public void onRender(LimitedInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        getHelper(screen.player).onRender(graphics, screen);
    }
}
