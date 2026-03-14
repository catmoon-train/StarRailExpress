
package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.ReplayDisplayUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class RoleStatsPanel implements Renderable, GuiEventListener, NarratableEntry {
    private final SREPlayerStatsComponent stats; // 玩家统计数据组件
    private SRERole selectedRole; // 当前选中的角色
    private SREPlayerStatsComponent.RoleStats selectedRoleStats; // 当前选中角色的统计数据
    private ScrollableRoleListComponent roleListComponent; // 滚动角色列表组件
    private RoleDetailsComponent roleDetailsComponent; // 角色详情组件
    private EditBox searchBox; // 搜索框

    private final int x; // 组件X坐标
    private final int y; // 组件Y坐标
    private final int width; // 组件宽度
    private final int height; // 组件高度
    private boolean visible = true; // 组件是否可见
    private final List<GuiEventListener> children = new ArrayList<>(); // 子事件监听器列表
    private final List<Renderable> renderables = new ArrayList<>(); // 可渲染对象列表
    private static void enableScissor(float x0, float y0, float x1, float y1) {
        Window window = Minecraft.getInstance().getWindow();
        int screenHeight = window.getScreenHeight();
        int scissorX = (int) (x0 * window.getGuiScale());
        int scissorY = (int) (screenHeight - (y1 * window.getGuiScale()));
        int scissorWidth = (int) ((x1 - x0) * window.getGuiScale());
        int scissorHeight = (int) ((y1 - y0) * window.getGuiScale());
        RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }
    /**
     * 构造函数
     * @param x X坐标
     * @param y Y坐标
     * @param width 宽度
     * @param height 高度
     * @param stats 玩家统计数据
     */
    public RoleStatsPanel(int x, int y, int width, int height, SREPlayerStatsComponent stats) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.stats = stats;
        setupComponents();
    }

    // 获取组件位置和尺寸的方法
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * 添加可渲染组件
     * @param renderable 可渲染对象
     */
    private void addRenderableWidget(Renderable renderable) {
        if (renderable instanceof GuiEventListener) {
            children.add((GuiEventListener) renderable);
        }
        renderables.add(renderable);
    }

    /**
     * 添加事件监听组件
     * @param widget GUI事件监听器
     */
    private void addWidget(GuiEventListener widget) {
        children.add(widget);
        if (widget instanceof Renderable) {
            renderables.add((Renderable) widget);
        }
    }

    /**
     * 添加组件到渲染列表
     * @param component 要添加的组件
     */
    private void addComponent(Renderable component) {
        renderables.add(component);
    }

    /**
     * 设置界面组件
     */
    private void setupComponents() {
        int rightPanelContentX = getX();
        int rightPanelContentWidth = getWidth();
        int currentY = getY();

        titleY = currentY;
        currentY += Minecraft.getInstance().font.lineHeight + 15;

        // 创建搜索框
        searchBox = new EditBox(
                Minecraft.getInstance().font,
                rightPanelContentX,
                currentY,
                rightPanelContentWidth,
                20,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.search_role")
        );
        searchBox.setHint(Component.translatable("screen." + SRE.MOD_ID + ".player_stats.search_role"));
        searchBox.setMaxLength(50);
        searchBox.setResponder(this::onSearchTextChanged);
        this.addRenderableWidget(searchBox);
        currentY += searchBox.getHeight() + 10;

        // 计算可用高度并创建角色列表
        int availableHeight = getHeight() - (currentY - getY()) - 15;
        int roleListHeight = Math.max(50, availableHeight / 2);
        roleListComponent = new ScrollableRoleListComponent(
                rightPanelContentX,
                currentY,
                rightPanelContentWidth,
                roleListHeight,
                this::onRoleSelected
        );

        // 准备角色数据并排序
        List<SRERole> roles = new ArrayList<>();
        Map<ResourceLocation, SREPlayerStatsComponent.RoleStats> roleStatsMap = stats.getRoleStats();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (roleStatsMap.containsKey(role.identifier())) {
                roles.add(role);
            }
        }
        roles.sort(Comparator.comparing(role -> role.identifier().getPath()));
        roleListComponent.setRoles(roles, roleStatsMap);
        this.addRenderableWidget(roleListComponent);
        currentY += roleListHeight + 15;

        // 创建角色详情组件
        roleDetailsComponent = new RoleDetailsComponent(
                rightPanelContentX,
                currentY,
                rightPanelContentWidth,
                getHeight() - (currentY - getY())
        );
        this.addRenderableWidget(roleDetailsComponent);

        // 如果有角色数据，则选择第一个角色
        if (!roles.isEmpty()) {
            onRoleSelected(roles.getFirst(), roleStatsMap.get(roles.getFirst().identifier()));
        }
        searchBox.setFocused(true);
    }

    private int titleY; // 标题Y坐标

    /**
     * 处理搜索文本变化
     * @param searchText 搜索文本
     */
    private void onSearchTextChanged(String searchText) {
        roleListComponent.filterRoles(searchText);
    }

    /**
     * 处理角色选择事件
     * @param role 选中的角色
     * @param roleStats 选中角色的统计数据
     */
    private void onRoleSelected(SRERole role, SREPlayerStatsComponent.RoleStats roleStats) {
        selectedRole = role;
        selectedRoleStats = roleStats;
        roleDetailsComponent.setRole(role, roleStats);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.role_stats").withStyle(style -> style.withBold(true)),
                getX(), titleY, 0xFFFFFFFF);

        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, delta);
        }
    }

    /**
     * 设置组件可见性
     * @param visible 是否可见
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        for (GuiEventListener child : children) {
            if (child != searchBox && child.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(chr, modifiers);
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (GuiEventListener child : children) {
            if (child != searchBox && child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (roleListComponent.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        for (GuiEventListener child : children) {
            if (child != roleListComponent && child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (roleListComponent.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        for (GuiEventListener child : children) {
            if (child != roleListComponent && child.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFocused() {
        return searchBox.isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        searchBox.setFocused(focused);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    }

    /**
     * 获取当前选中的角色
     * @return 选中的角色
     */
    public SRERole getSelectedRole() {
        return selectedRole;
    }

    /**
     * 获取当前选中角色的统计数据
     * @return 角色统计数据
     */
    public SREPlayerStatsComponent.RoleStats getSelectedRoleStats() {
        return selectedRoleStats;
    }

    /**
     * 滚动角色列表组件 - 用于显示可滚动的角色列表
     */
    private static class ScrollableRoleListComponent extends AbstractWidget {
        private final BiConsumer<SRERole, SREPlayerStatsComponent.RoleStats> onRoleSelected; // 角色选择回调
        private List<SRERole> allRoles; // 所有角色列表
        private Map<ResourceLocation, SREPlayerStatsComponent.RoleStats> roleStatsMap; // 角色统计数据映射
        private List<SRERole> filteredRoles; // 过滤后的角色列表
        private double scrollAmount = 0.0; // 滚动量
        private static final int SCROLLBAR_WIDTH = 6; // 滚动条宽度
        private final int itemHeight = 24; // 每个项目的高度
        private SRERole selectedRole; // 选中的角色
        private double initialMouseY = -1; // 初始鼠标Y坐标（用于拖拽滚动）
        private double initialScrollAmount; // 初始滚动量

        /**
         * 构造函数
         * @param x X坐标
         * @param y Y坐标
         * @param width 宽度
         * @param height 高度
         * @param onRoleSelected 角色选择回调
         */
        public ScrollableRoleListComponent(int x, int y, int width, int height, BiConsumer<SRERole, SREPlayerStatsComponent.RoleStats> onRoleSelected) {
            super(x, y, width, height, Component.empty());
            this.onRoleSelected = onRoleSelected;
            this.allRoles = new ArrayList<>();
            this.filteredRoles = new ArrayList<>();
        }

        /**
         * 设置角色列表
         * @param roles 角色列表
         * @param roleStatsMap 角色统计数据映射
         */
        public void setRoles(List<SRERole> roles, Map<ResourceLocation, SREPlayerStatsComponent.RoleStats> roleStatsMap) {
            this.allRoles = roles;
            this.roleStatsMap = roleStatsMap;
            this.filteredRoles = new ArrayList<>(roles);
            if (!filteredRoles.isEmpty()) {
                selectedRole = filteredRoles.getFirst();
            }
        }

        /**
         * 根据搜索文本过滤角色
         * @param searchText 搜索文本
         */
        public void filterRoles(String searchText) {
            if (searchText == null || searchText.isEmpty()) {
                filteredRoles = new ArrayList<>(allRoles);
            } else {
                filteredRoles = allRoles.stream()
                        .filter(role -> ReplayDisplayUtils.getRoleDisplayName(role.identifier().toString()).getString().toLowerCase().contains(searchText.toLowerCase()))
                        .toList();
            }
            setScrollAmount(0.0);
            if (!filteredRoles.isEmpty() && (selectedRole == null || !filteredRoles.contains(selectedRole))) {
                selectedRole = filteredRoles.getFirst();
                onRoleSelected.accept(selectedRole, roleStatsMap.get(selectedRole.identifier()));
            } else if (filteredRoles.isEmpty()) {
                selectedRole = null;
                onRoleSelected.accept(null, null);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            // 启用剪切矩形以限制渲染区域
            enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
            
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x80000000);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFAAAAAA);

            int visibleItemCount = getHeight() / itemHeight;
            int startIndex = (int) (getScrollAmount() / itemHeight);
            int endIndex = Math.min(startIndex + visibleItemCount + 1, filteredRoles.size()); // +1 确保部分项目被渲染

            // 渲染可见的角色项
            for (int i = startIndex; i < endIndex; i++) {
                SRERole role = filteredRoles.get(i);
                SREPlayerStatsComponent.RoleStats stats = roleStatsMap.get(role.identifier());
                int itemY = getY() + (i * itemHeight) - (int) getScrollAmount();
                boolean isSelected = role.equals(selectedRole);
                graphics.fill(getX(), itemY, getX() + getWidth(), itemY + itemHeight, isSelected ? 0x60FFFFFF : 0x20FFFFFF);

                Component displayName = ReplayDisplayUtils.getRoleDisplayName(role.identifier().toString());
                int nameWidth = Minecraft.getInstance().font.width(displayName);
                int maxNameWidth = getWidth() - 25;
                if (nameWidth > maxNameWidth) {
                    String truncatedText = Minecraft.getInstance().font.plainSubstrByWidth(displayName.getString(), maxNameWidth - 3) + "...";
                    graphics.drawString(Minecraft.getInstance().font, truncatedText, getX() + 5, itemY + 5, role.getColor());
                } else {
                    graphics.drawString(Minecraft.getInstance().font, displayName, getX() + 5, itemY + 5, role.getColor());
                }

                if (stats != null) {
                    String briefStats = String.format("游玩：%d, 胜利：%d", stats.getTimesPlayed(), stats.getWinsAsRole());
                    int statsWidth = Minecraft.getInstance().font.width(briefStats);
                    int maxStatsWidth = getWidth() - 25;
                    if (statsWidth > maxStatsWidth) {
                        String truncatedStats = Minecraft.getInstance().font.plainSubstrByWidth(briefStats, maxStatsWidth - 3) + "...";
                        graphics.drawString(Minecraft.getInstance().font, truncatedStats, getX() + 5, itemY + 15, 0xFFCCCCCC);
                    } else {
                        graphics.drawString(Minecraft.getInstance().font, briefStats, getX() + 5, itemY + 15, 0xFFCCCCCC);
                    }
                }
            }

            // 渲染滚动条
            if (scrollbarVisible()) {
                int scrollbarX = getX() + getWidth() - SCROLLBAR_WIDTH;
                int scrollbarHeight = (int) ((float) getHeight() * getHeight() / getMaxPosition());
                scrollbarHeight = Math.max(32, scrollbarHeight); // 最小滚动条高度
                scrollbarHeight = Math.min(getHeight() - 8, scrollbarHeight); // 最大滚动条高度

                int scrollbarY = getY() + (int) (getScrollAmount() * (getHeight() - scrollbarHeight) / getMaxScroll());
                if (scrollbarY < getY()) {
                    scrollbarY = getY();
                }

                graphics.fill(scrollbarX, getY(), scrollbarX + SCROLLBAR_WIDTH, getY() + getHeight(), 0x80000000); // 滚动条背景
                graphics.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, 0xFFAAAAAA); // 滚动条手柄
            }
            
            // 禁用剪切矩形
            RenderSystem.disableScissor();
        }

        /**
         * 检查滚动条是否可见
         * @return 滚动条是否可见
         */
        private boolean scrollbarVisible() {
            return getMaxScroll() > 0;
        }

        /**
         * 获取最大滚动值
         * @return 最大滚动值
         */
        private int getMaxScroll() {
            return Math.max(0, filteredRoles.size() * itemHeight - getHeight());
        }

        /**
         * 获取当前滚动量
         * @return 当前滚动量
         */
        private double getScrollAmount() {
            return scrollAmount;
        }

        /**
         * 设置滚动量
         * @param amount 滚动量
         */
        private void setScrollAmount(double amount) {
            scrollAmount = Math.max(0, Math.min(amount, getMaxScroll()));
        }

        /**
         * 获取最大位置
         * @return 最大位置
         */
        private int getMaxPosition() {
            return filteredRoles.size() * itemHeight;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) return false;

            boolean isScrollbarClicked = mouseX >= getX() + getWidth() - SCROLLBAR_WIDTH && mouseX <= getX() + getWidth();
            if (isScrollbarClicked && scrollbarVisible()) {
                initialMouseY = mouseY;
                initialScrollAmount = getScrollAmount();
                return true;
            }

            int relativeY = (int) (mouseY - getY() + getScrollAmount());
            int clickedIndex = relativeY / itemHeight;

            if (clickedIndex >= 0 && clickedIndex < filteredRoles.size()) {
                selectedRole = filteredRoles.get(clickedIndex);
                onRoleSelected.accept(selectedRole, roleStatsMap.get(selectedRole.identifier()));
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            setScrollAmount(getScrollAmount() - scrollY * itemHeight / 2.0);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button == 0 && isMouseOver(mouseX, mouseY)) {
                boolean isDraggingScrollbar = mouseX >= getX() + getWidth() - SCROLLBAR_WIDTH && mouseX <= getX() + getWidth();

                if (isDraggingScrollbar && scrollbarVisible()) {
                    int scrollbarHeight = (int) ((float) getHeight() * getHeight() / getMaxPosition());
                    scrollbarHeight = Math.max(32, scrollbarHeight);
                    scrollbarHeight = Math.min(getHeight() - 8, scrollbarHeight);

                    double scrollRange = getHeight() - scrollbarHeight;
                    double scrollPerPixel = (double) getMaxScroll() / scrollRange;

                    double newScrollAmount = initialScrollAmount + (mouseY - initialMouseY) * scrollPerPixel;
                    setScrollAmount(newScrollAmount);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }

    /**
     * 角色详情组件 - 显示选中角色的详细统计数据
     */
    private static class RoleDetailsComponent extends AbstractWidget {
        private SRERole role; // 角色
        private SREPlayerStatsComponent.RoleStats roleStats; // 角色统计数据

        /**
         * 构造函数
         * @param x X坐标
         * @param y Y坐标
         * @param width 宽度
         * @param height 高度
         */
        public RoleDetailsComponent(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        /**
         * 设置要显示的角色和统计数据
         * @param role 角色
         * @param roleStats 角色统计数据
         */
        public void setRole(SRERole role, SREPlayerStatsComponent.RoleStats roleStats) {
            this.role = role;
            this.roleStats = roleStats;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (role == null || roleStats == null) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x80000000);
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFAAAAAA);
                return;
            }

            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight() + 10, 0x80000000);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight() + 10, 0xFFAAAAAA);

            int currentY = getY() + 5;
            Component roleName = ReplayDisplayUtils.getRoleDisplayName(role.identifier().toString());
            graphics.drawString(Minecraft.getInstance().font,
                    roleName.copy().withStyle(style -> style.withBold(true)),
                    getX() + 5, currentY, role.getColor());
            currentY += Minecraft.getInstance().font.lineHeight + 10;

            // 渲染各种统计数据
            drawStatLine(graphics, currentY, "screen." + SRE.MOD_ID + ".player_stats.times_played", roleStats.getTimesPlayed());
            currentY += 10;
            drawStatLine(graphics, currentY, "screen." + SRE.MOD_ID + ".player_stats.wins", roleStats.getWinsAsRole());
            currentY += 10;
            drawStatLine(graphics, currentY, "screen." + SRE.MOD_ID + ".player_stats.kills", roleStats.getKillsAsRole());
            currentY += 10;
            drawStatLine(graphics, currentY, "screen." + SRE.MOD_ID + ".player_stats.team_kills", roleStats.getTeamKillsAsRole());
            currentY += 10;
            drawStatLine(graphics, currentY, "screen." + SRE.MOD_ID + ".player_stats.deaths", roleStats.getDeathsAsRole());
            currentY += 10;

            // 计算并显示胜率
            int gamesPlayed = roleStats.getTimesPlayed();
            int wins = roleStats.getWinsAsRole();
            double winRate = gamesPlayed > 0 ? (double) wins / gamesPlayed * 100 : 0.0;
            drawStatLine(graphics, currentY, "screen." + SRE.MOD_ID + ".player_stats.win_rate", String.format("%.2f%%", winRate));
            currentY += 10;

            // 计算并显示击杀死亡比
            int kills = roleStats.getKillsAsRole();
            int deaths = roleStats.getDeathsAsRole();
            double kdRatio = deaths > 0 ? (double) kills / deaths : kills;
            drawStatLine(graphics, currentY, "screen." + SRE.MOD_ID + ".player_stats.kd_ratio", String.format("%.2f", kdRatio));
        }

        /**
         * 绘制统计行
         * @param graphics 图形上下文
         * @param y Y坐标
         * @param translationKey 翻译键
         * @param value 值
         */
        private void drawStatLine(GuiGraphics graphics, int y, String translationKey, Object value) {
            Component text = Component.translatable(translationKey, value);
            graphics.drawString(Minecraft.getInstance().font, text, getX() + 10, y, 0xFFCCCCCC);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            // 不需要叙述
        }
    }
}