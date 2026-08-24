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

package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 角色背包界面辅助类：处理角色专用选人列表的通用逻辑——角色激活判断、分页、
 * 玩家名搜索与排序，内部使用 {@link PlayerPaginationHelper}。
 *
 * <p>
 * 配合
 * {@link io.wifi.starrailexpress.api.SRERole#setInventoryScreenExtensionFactory}
 * 与
 * {@link RoleInventoryScreenExtension} 接口，在客户端注册使用。
 */
public class RoleScreenHelper<T> {
    private final PlayerPaginationHelper<T> paginationHelper;
    private final BiConsumer<GuiGraphics, Point> extraDrawer;
    private final Supplier<List<T>> entriesSupplier;
    private final Predicate<LocalPlayer> isActivePredicate;
    private final LocalPlayer player;

    /**
     * @param player          客户端玩家实体
     * @param role            职业(需要为此职业也才能渲染)
     * @param widgetCreator   用于创建玩家小部件的回调
     * @param textProvider    分页文本提供器
     * @param extraDrawer     额外绘制逻辑（接收绘制上下文和屏幕中心点）
     * @param entriesSupplier 提供玩家条目列表的 Supplier
     */
    public RoleScreenHelper(LocalPlayer player,
            SRERole role,
            PlayerPaginationHelper.PlayerWidgetCreator<T> widgetCreator,
            PlayerPaginationHelper.PaginationTextProvider textProvider,
            BiConsumer<GuiGraphics, Point> extraDrawer,
            Supplier<List<T>> entriesSupplier) {
        this(player, widgetCreator, textProvider, extraDrawer, entriesSupplier,
                (p) -> SREClient.gameComponent != null && SREClient.gameComponent.isRole(p, role));
    }

    /**
     * @param player            客户端玩家实体
     * @param widgetCreator     用于创建玩家小部件的回调
     * @param textProvider      分页文本提供器
     * @param extraDrawer       额外绘制逻辑（接收绘制上下文和屏幕中心点）
     * @param entriesSupplier   提供玩家条目列表的 Supplier
     * @param isActivePredicate 判断是否渲染的条件
     */
    public RoleScreenHelper(LocalPlayer player,
            PlayerPaginationHelper.PlayerWidgetCreator<T> widgetCreator,
            PlayerPaginationHelper.PaginationTextProvider textProvider,
            BiConsumer<GuiGraphics, Point> extraDrawer,
            Supplier<List<T>> entriesSupplier, Predicate<LocalPlayer> isActivePredicate) {
        this.player = player;
        this.paginationHelper = new PlayerPaginationHelper<>(widgetCreator, textProvider);
        this.extraDrawer = extraDrawer;
        this.entriesSupplier = entriesSupplier;
        this.isActivePredicate = isActivePredicate;
    }

    /**
     * @param player          客户端玩家实体
     * @param widgetCreator   用于创建玩家小部件的回调
     * @param textProvider    分页文本提供器
     * @param extraDrawer     额外绘制逻辑（接收绘制上下文和屏幕中心点）
     * @param entriesSupplier 提供玩家条目列表的 Supplier
     */
    public RoleScreenHelper(LocalPlayer player,
            PlayerPaginationHelper.PlayerWidgetCreator<T> widgetCreator,
            PlayerPaginationHelper.PaginationTextProvider textProvider,
            BiConsumer<GuiGraphics, Point> extraDrawer,
            Supplier<List<T>> entriesSupplier) {
        this(player, widgetCreator, textProvider, extraDrawer, entriesSupplier, (a) -> true);
    }

    /**
     * 检查当前玩家是否拥有该角色。
     */
    public boolean isRoleActive() {
        return isActivePredicate.test(player);
    }

    /**
     * 渲染时调用：绘制角色特定内容与分页信息。
     */
    public void onRender(GuiGraphics context, LimitedInventoryScreen screen) {
        if (!isRoleActive()) {
            return;
        }
        int y = (screen.height - 32) / 2;
        int x = screen.width / 2;
        if (extraDrawer != null) {
            extraDrawer.accept(context, new Point(x, y));
        }
        paginationHelper.drawPagination(context, screen, y);
    }

    /**
     * 初始化时调用：清除旧控件、填充条目并添加当前页控件。
     */
    public void onInit(LimitedInventoryScreen screen) {
        if (!isRoleActive()) {
            return;
        }
        paginationHelper.clearManagedWidgets(screen);
        List<T> entries = entriesSupplier.get();
        paginationHelper.setPlayerEntries(entries);
        paginationHelper.addPageWidgets(screen);
    }

    /**
     * 获取分页助手，用于直接操作（例如翻页/刷新）。
     */
    public PlayerPaginationHelper<T> getPaginationHelper() {
        return paginationHelper;
    }

    // ===== 便捷方法（轮椅） =====

    /** 启用按玩家名搜索与按名排序（忽略大小写）。 */
    public RoleScreenHelper<T> setNameExtractor(Function<T, String> nameExtractor) {
        paginationHelper.setNameExtractor(nameExtractor);
        return this;
    }

    /** 自定义排序（覆盖默认的按名排序）。 */
    public RoleScreenHelper<T> setSort(Comparator<T> sortComparator) {
        paginationHelper.setSort(sortComparator);
        return this;
    }

    /** 便捷：挂载玩家名搜索框（默认位置：列表上方居中）。 */
    public EditBox attachSearchBox(LimitedInventoryScreen screen) {
        return paginationHelper.attachSearchBox(screen);
    }

    /** 便捷：在指定位置挂载玩家名搜索框。 */
    public EditBox attachSearchBox(LimitedInventoryScreen screen, int x, int y, int width, int height) {
        return paginationHelper.attachSearchBox(screen, x, y, width, height);
    }
}
