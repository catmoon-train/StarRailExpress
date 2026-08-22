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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 玩家列表分页辅助类：为背包界面（{@link LimitedInventoryScreen}）提供"选人列表"的
 * 轮椅方法——翻页（{@link #nextPage} / {@link #prevPage} / {@link #jumpToPage}）、
 * 按玩家名搜索（{@link #attachSearchBox} 输入框实时过滤）、按玩家名排序
 * （{@link #setNameExtractor} 默认按名 / {@link #setSort} 自定义）。
 *
 * <p>搜索与排序都是可选的：默认保持传入条目的原始顺序；调用
 * {@link #setNameExtractor(Function)} 后才会启用"按名搜索 + 按名排序"。
 */
public class PlayerPaginationHelper<T> {
    /** 每页显示的玩家数。 */
    public static final int PLAYERS_PER_PAGE = 8;

    /** 搜索框提示文字翻译键。 */
    public static final String SEARCH_BOX_TRANSLATION_KEY = "gui.starrailexpress.role_screen.search";

    // 分页状态
    private int currentPage = 0;
    private List<T> playerEntries = List.of();

    // 搜索 / 排序（可选，默认不启用）
    private Function<T, String> nameExtractor = null; // 设置后启用"按名搜索 + 默认按名排序"
    private Comparator<T> sortComparator = null;      // 自定义排序；为空时若启用 nameExtractor 则按名排序
    private String searchQuery = "";
    private EditBox searchBox = null;

    // 受管控件
    private final List<Button> managedButtons = new ArrayList<>();
    private final List<Button> managedPlayerWidgets = new ArrayList<>();

    // 回调
    private final PlayerWidgetCreator<T> widgetCreator;
    private final PaginationTextProvider textProvider;

    /**
     * 创建玩家控件的回调。实现中请调用 {@code screen.addRoleWidget(widget)} 把控件挂到屏幕上。
     */
    @FunctionalInterface
    public interface PlayerWidgetCreator<T> {
        Button createWidget(LimitedInventoryScreen screen, int x, int y, T playerEntry, int index);
    }

    /**
     * 分页文案翻译键提供器。
     */
    public interface PaginationTextProvider {
        String getPageTranslationKey();

        String getPrevTranslationKey();

        String getNextTranslationKey();
    }

    public PlayerPaginationHelper(PlayerWidgetCreator<T> widgetCreator, PaginationTextProvider textProvider) {
        this.widgetCreator = widgetCreator;
        this.textProvider = textProvider;
    }

    // ===== 搜索 / 排序 =====

    /** 设置玩家名提取器；设置后启用按名搜索与默认按名排序（忽略大小写）。 */
    public PlayerPaginationHelper<T> setNameExtractor(Function<T, String> nameExtractor) {
        this.nameExtractor = nameExtractor;
        return this;
    }

    public Function<T, String> getNameExtractor() {
        return nameExtractor;
    }

    /** 自定义排序；为空时若启用了 {@link #setNameExtractor} 则按名字忽略大小写排序。 */
    public PlayerPaginationHelper<T> setSort(Comparator<T> sortComparator) {
        this.sortComparator = sortComparator;
        return this;
    }

    public Comparator<T> getSortComparator() {
        return sortComparator;
    }

    /** 设置搜索关键词（自动回到第一页）。 */
    public PlayerPaginationHelper<T> setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query.trim();
        this.currentPage = 0;
        return this;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public boolean isSearchActive() {
        return nameExtractor != null && !searchQuery.isEmpty();
    }

    /**
     * 创建并挂载玩家名搜索框（原版 {@link EditBox}，输入实时过滤）。
     * 搜索框独立于分页控件管理，翻页/刷新不会清除它。
     * 每次挂载都会把输入回调重新绑定到当前 screen，避免跨屏幕实例时刷新旧屏幕。
     */
    public EditBox attachSearchBox(LimitedInventoryScreen screen, int x, int y, int width, int height) {
        if (searchBox == null) {
            searchBox = new EditBox(Minecraft.getInstance().font, x, y, width, height,
                    Component.translatable(SEARCH_BOX_TRANSLATION_KEY));
            searchBox.setMaxLength(64);
            searchBox.setValue(searchQuery);
        } else {
            searchBox.setX(x);
            searchBox.setY(y);
            searchBox.setWidth(width);
            searchBox.setHeight(height);
            screen.removeRoleWidget(searchBox);
        }
        searchBox.setResponder(query -> {
            setSearchQuery(query);
            refreshPage(screen);
        });
        screen.addRoleWidget(searchBox);
        return searchBox;
    }

    /** 便捷重载：搜索框默认放在选人列表上方居中（避免与头像控件重叠）。 */
    public EditBox attachSearchBox(LimitedInventoryScreen screen) {
        int width = 120;
        int centerY = (screen.height - 32) / 2;
        // 提示文字在 centerY+40，头像控件贴图顶部约在 centerY+73；搜索框放在两者之间
        return attachSearchBox(screen, screen.width / 2 - width / 2, centerY + 52, width, 16);
    }

    // ===== 条目 =====

    /** 设置玩家条目（过滤与排序在显示时计算）。 */
    public void setPlayerEntries(List<T> playerEntries) {
        this.playerEntries = List.copyOf(playerEntries);
        this.currentPage = 0;
    }

    public List<T> getPlayerEntries() {
        return playerEntries;
    }

    /** 经过搜索过滤 + 排序后的可见条目。 */
    public List<T> getVisibleEntries() {
        List<T> visible = playerEntries;
        if (isSearchActive()) {
            String needle = searchQuery.toLowerCase(Locale.ROOT);
            visible = playerEntries.stream()
                    .filter(e -> nameOf(e).toLowerCase(Locale.ROOT).contains(needle))
                    .collect(Collectors.toList());
        }
        Comparator<T> comparator = sortComparator;
        if (comparator == null && nameExtractor != null) {
            comparator = Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER);
        }
        if (comparator != null) {
            List<T> sorted = new ArrayList<>(visible);
            sorted.sort(comparator);
            return sorted;
        }
        return visible;
    }

    private String nameOf(T entry) {
        if (nameExtractor == null) {
            return "";
        }
        String name = nameExtractor.apply(entry);
        return name == null ? "" : name;
    }

    // ===== 渲染 / 控件 =====

    /** 绘制分页信息（"第 x / y 页"）。 */
    public void drawPagination(GuiGraphics context, LimitedInventoryScreen screen, int centerY) {
        int totalPages = getTotalPages();
        if (totalPages > 1) {
            Component pageText = Component.translatable(textProvider.getPageTranslationKey(), currentPage + 1,
                    totalPages);
            int pageTextWidth = Minecraft.getInstance().font.width(pageText);
            context.drawString(Minecraft.getInstance().font, pageText,
                    screen.width / 2 - pageTextWidth / 2,
                    centerY + 120, Color.WHITE.getRGB());
        }
    }

    /** 为当前页添加玩家控件与翻页按钮。 */
    public void addPageWidgets(LimitedInventoryScreen screen) {
        List<T> visible = getVisibleEntries();
        int totalPages = (int) Math.ceil((double) visible.size() / PLAYERS_PER_PAGE);
        if (totalPages == 0) {
            return;
        }
        currentPage = Mth.clamp(currentPage, 0, totalPages - 1);

        int apart = 36;
        int startIndex = currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, visible.size());
        int visibleCount = endIndex - startIndex;
        int x = screen.width / 2 - visibleCount * apart / 2 + 9;
        int centerY = (screen.height - 32) / 2;
        int y = centerY + 80;

        for (int i = startIndex; i < endIndex; ++i) {
            T playerEntry = visible.get(i);
            Button playerWidget = widgetCreator.createWidget(screen, x + apart * (i - startIndex), y, playerEntry, i);
            if (playerWidget != null) {
                managedPlayerWidgets.add(playerWidget);
            }
        }

        if (totalPages > 1) {
            int buttonY = y + 40;
            Button prevButton = Button
                    .builder(Component.translatable(textProvider.getPrevTranslationKey()), button -> prevPage(screen))
                    .bounds(screen.width / 2 - 80, buttonY, 50, 20).build();
            Button nextButton = Button
                    .builder(Component.translatable(textProvider.getNextTranslationKey()), button -> nextPage(screen))
                    .bounds(screen.width / 2 + 30, buttonY, 50, 20).build();
            managedButtons.add(prevButton);
            managedButtons.add(nextButton);
            screen.addRoleWidget(prevButton);
            screen.addRoleWidget(nextButton);
        }
    }

    /** 仅移除本辅助类管理的控件（翻页按钮 + 玩家控件，不含搜索框）。 */
    public void clearManagedWidgets(LimitedInventoryScreen screen) {
        for (Button button : managedButtons) {
            screen.removeRoleWidget(button);
        }
        managedButtons.clear();
        for (Button widget : managedPlayerWidgets) {
            screen.removeRoleWidget(widget);
        }
        managedPlayerWidgets.clear();
    }

    /** 刷新当前页。 */
    public void refreshPage(LimitedInventoryScreen screen) {
        clearManagedWidgets(screen);
        addPageWidgets(screen);
    }

    // ===== 翻页（轮椅方法） =====

    public void nextPage(LimitedInventoryScreen screen) {
        jumpToPage(screen, currentPage + 1);
    }

    public void prevPage(LimitedInventoryScreen screen) {
        jumpToPage(screen, currentPage - 1);
    }

    public void jumpToPage(LimitedInventoryScreen screen, int page) {
        int target = Mth.clamp(page, 0, Math.max(0, getTotalPages() - 1));
        if (target != currentPage) {
            currentPage = target;
            refreshPage(screen);
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return playerEntries.isEmpty() ? 0 : (int) Math.ceil((double) getVisibleEntries().size() / PLAYERS_PER_PAGE);
    }
}
