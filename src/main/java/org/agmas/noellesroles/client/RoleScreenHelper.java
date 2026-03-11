package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import java.awt.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

/**
 * 辅助类用于处理角色屏幕的通用逻辑，如分页和角色检查。
 */
public class RoleScreenHelper<T> {
    private final LocalPlayer player;
    private final PlayerPaginationHelper<T> paginationHelper;
    private final Role role;
    private final BiConsumer<GuiGraphics, Point> extraDrawer;
    private final Supplier<List<T>> entriesSupplier;

    /**
     * 创建 RoleScreenHelper 实例。
     * @param player 客户端玩家实体
     * @param role 对应的角色
     * @param widgetCreator 用于创建玩家小部件的回调
     * @param textProvider 分页文本提供器
     * @param extraDrawer 额外绘制逻辑（接收绘制上下文和屏幕中心点）
     * @param entriesSupplier 提供玩家条目列表的 Supplier
     */
    public RoleScreenHelper(LocalPlayer player,
                            Role role,
                            PlayerPaginationHelper.PlayerWidgetCreator<T> widgetCreator,
                            PlayerPaginationHelper.PaginationTextProvider textProvider,
                            BiConsumer<GuiGraphics, Point> extraDrawer,
                            Supplier<List<T>> entriesSupplier) {
        this.player = player;
        this.role = role;
        this.paginationHelper = new PlayerPaginationHelper<>(widgetCreator, textProvider);
        this.extraDrawer = extraDrawer;
        this.entriesSupplier = entriesSupplier;
    }

    /**
     * 检查当前玩家是否拥有该角色。
     */
    public boolean isRoleActive() {
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.level());
        return gameWorldComponent.isRole(player, role);
    }

    /**
     * 在渲染时调用，绘制角色特定内容和分页。
     * @param context 绘制上下文
     * @param screen 屏幕实例（必须实现 ScreenWithChildren）
     */
    public void onRender(GuiGraphics context, PlayerPaginationHelper.ScreenWithChildren screen) {
        if (!isRoleActive()) {
            return;
        }
        Screen screenAsScreen = (Screen) screen;
        int y = (screenAsScreen.height - 32) / 2;
        int x = screenAsScreen.width / 2;
        if (extraDrawer != null) {
            extraDrawer.accept(context, new Point(x, y));
        }
        // 绘制分页信息，需要 Screen 类型，可以安全转换
        paginationHelper.drawPagination(context, screenAsScreen, y);
    }

    /**
     * 在初始化时调用，设置分页条目并添加小部件。
     * @param screen 屏幕实例（必须实现 ScreenWithChildren）
     */
    public void onInit(PlayerPaginationHelper.ScreenWithChildren screen) {
        if (!isRoleActive()) {
            return;
        }
        // 只清除由分页助手管理的小部件，而不是所有小部件
        paginationHelper.clearManagedWidgets(screen);
        List<T> entries = entriesSupplier.get();
        paginationHelper.setPlayerEntries(entries);
        paginationHelper.addPageWidgets((Screen) screen);
    }

    /**
     * 获取分页助手，用于直接操作（例如刷新页面）。
     */
    public PlayerPaginationHelper<T> getPaginationHelper() {
        return paginationHelper;
    }
}