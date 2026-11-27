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

package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.client.gui.widget.SreTabButton;
import io.wifi.starrailexpress.client.gui.widget.SwitchState;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiSurface;
import io.wifi.starrailexpress.client.ui.UiText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.screen.manage.ManageCatalog;
import org.agmas.noellesroles.client.screen.manage.ManageEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * <b>游戏设置中心</b>（{@code docs/ui_style.md} §8.7）：把原来散在各处的局设置收在一页。
 *
 * <h2>页面结构</h2>
 * 顶部分类页签（{@link SreTabButton}，与编辑器里的页签是同一个控件）+ 一个可滚动的行列表。
 * 行只有五种：分组标题、指令、导航（打开别的界面）、胶囊开关、数字步进器 —— 见 {@link ManageEntry}，
 * 具体有哪些行在 {@link ManageCatalog} 里声明，本类不写死任何一条设置。
 *
 * <h2>样式</h2>
 * 面板走 {@code UiSurface.panel}（纯色 + 1px 圆角暗铜描边），行是 {@code UiSurface.card}，
 * hover 只把描边换成黄铜；文字走 {@code UiText}（默认带硬阴影）。开关一律是胶囊开关
 * （{@code UiSurface.switchTrack}），不要再出现「文字 + [✓]」这种状态写法。
 *
 * <h2>与现成界面的关系（重要）</h2>
 * 职业轮选名单、地图轮换、自定义内容管理都是现成的界面，本页只做<b>入口</b>（{@link ManageEntry.Nav}）；
 * 真正的编辑逻辑留在它们自己那里，避免「两套界面各改一半」。
 *
 * <h2>状态从哪来</h2>
 * 职业 / 修饰符开关状态来自服务端同步下来的客户端镜像（{@code /manageRolesUI silent}），
 * 没同步到时显示胶囊开关的中间态而不是猜一个值；人数取服务端算好的「手动值 / 自动值」。
 */
public class GameManagementScreen extends Screen implements ManageCatalog.Context {

    // ══════════════════════════════════════════════════════════════════
    // 布局常量（全部落在 4px 栅格上）
    // ══════════════════════════════════════════════════════════════════

    private static final int PAD = 8;
    private static final int HEADER_H = 20;
    private static final int TAB_GAP = 2;
    private static final int SCROLL_W = UiSurface.SCROLL_WIDTH;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_GAP = 2;
    /** 开关行的胶囊尺寸（与 {@code UiSurface.SWITCH_H} 一致）。 */
    private static final int SWITCH_W = 18;
    private static final int SWITCH_H = UiSurface.SWITCH_H;
    /** 数字行的 [+] / [-] 方块。 */
    private static final int STEP_W = 13;
    private static final int STEP_H = 14;
    private static final int VALUE_W = 36;
    private static final int AUTO_W = 34;

    private final Screen parent;
    private final List<ManageEntry> entries = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int tabBarY;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;

    private int selectedCatIdx;
    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;
    private double dragStartY;
    private int dragStartOffset;

    /** 搜索词：直接键入即可过滤当前分类的行（空串 = 不过滤）。 */
    private String filter = "";
    /** 只在本页实例首次打开时自动同步一次（窗口缩放会重新 init，不重复发指令）。 */
    private boolean syncedOnce;
    /**
     * 需要重建页签控件（延迟到 render 开头执行）。
     *
     * <p>
     * 点页签时正处在 {@code children()} 的遍历里，此时 {@code clearWidgets()} 会当场改动那个列表；
     * 所以状态的变更与控件的重建分两步：回调里只置位，帧开始再重建。
     */
    private boolean tabsNeedRebuild;
    /** 「开始游戏」用的补充时长（分钟，0 = 用服务端默认）。 */
    private int startMinutes;
    /** 悬停动画（每条目一个进度值）。 */
    private final float[] hoverAnims = new float[1024];

    public GameManagementScreen() {
        this(null);
    }

    public GameManagementScreen(Screen parent) {
        super(Component.translatable("screen.game_manage.title"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 初始化与列表重建
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        computeLayout();
        rebuildTabs();
        rebuildEntries();
        // 打开时向服务端要一次职业 / 修饰符开关状态（静默指令，不会弹原版配置界面）
        if (!syncedOnce) {
            syncedOnce = true;
            requestRoleStateSync();
        }
    }

    private void computeLayout() {
        panelW = Math.min(this.width - 32, 720);
        panelH = this.height - 76;
        panelX = (this.width - panelW) / 2;
        panelY = 40;
        tabBarY = panelY - 24;

        contentX = panelX + PAD;
        contentY = panelY + 1 + HEADER_H + 4;
        contentW = panelW - PAD * 2 - SCROLL_W - 4;
        contentH = panelY + panelH - 4 - contentY;
    }

    /** 页签（真正的控件，外观与编辑器里的页签完全一致）。 */
    private void rebuildTabs() {
        rebuildTabsOnly();
    }

    private void rebuildTabsOnly() {
        clearWidgets();
        int count = ManageCatalog.CATEGORIES.size();
        int[] naturalW = new int[count];
        int total = TAB_GAP * (count - 1);
        for (int i = 0; i < count; i++) {
            naturalW[i] = font.width(categoryLabel(i)) + 16;
            total += naturalW[i];
        }
        float scale = total > panelW ? (float) panelW / total : 1.0F;
        int x = panelX;
        for (int i = 0; i < count; i++) {
            int tabW = Math.max(24, (int) (naturalW[i] * scale));
            final int index = i;
            addRenderableWidget(new SreTabButton(font, x, tabBarY, tabW, Component.translatable(labelKey(i)),
                    i == selectedCatIdx, () -> selectCategory(index)));
            x += tabW + TAB_GAP;
        }
    }

    private void rebuildEntries() {
        entries.clear();
        entries.addAll(ManageCatalog.build(selectedCatIdx, filter, this));
        java.util.Arrays.fill(hoverAnims, 0F);
        maxScroll = Math.max(0, totalEntriesHeight() - contentH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private void selectCategory(int index) {
        if (index == selectedCatIdx) {
            return;
        }
        selectedCatIdx = Mth.clamp(index, 0, ManageCatalog.CATEGORIES.size() - 1);
        scrollOffset = 0;
        filter = "";
        // 不在回调里 clearWidgets()：这里正处在 children() 的遍历中，重建推迟到 render 开头
        tabsNeedRebuild = true;
        rebuildEntries();
        click();
    }

    private String labelKey(int category) {
        return ManageCatalog.CATEGORIES.get(category).labelKey();
    }

    private String categoryLabel(int category) {
        return Component.translatable(labelKey(category)).getString();
    }

    private int categoryColor() {
        return ManageCatalog.CATEGORIES.get(selectedCatIdx).color();
    }

    // ══════════════════════════════════════════════════════════════════
    // ManageCatalog.Context
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void command(String command) {
        if (command == null || command.isBlank() || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        MutableComponent hint = Component.translatable("tip.game_manage.send_command",
                Component.literal("/" + command).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/" + command)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("tip.game_manage.click_to_suggest"))));
        this.minecraft.player.displayClientMessage(hint, false);
        this.minecraft.player.connection.sendCommand(command);
    }

    @Override
    public void open(Supplier<Screen> screen) {
        if (this.minecraft != null && screen != null) {
            this.minecraft.setScreen(screen.get());
        }
    }

    @Override
    public void refresh() {
        requestRoleStateSync();
        rebuildEntries();
    }

    @Override
    public Screen self() {
        return this;
    }

    @Override
    public int startMinutes() {
        return startMinutes;
    }

    /** 静默同步：服务端收到后回一份职业 / 修饰符开关 + 人数的快照（不会弹界面）。 */
    private void requestRoleStateSync() {
        command("manageRolesUI silent");
    }

    // ══════════════════════════════════════════════════════════════════
    // 高度与几何
    // ══════════════════════════════════════════════════════════════════

    private int totalEntriesHeight() {
        int height = 0;
        for (int i = 0; i < entries.size(); i++) {
            height += entries.get(i).height();
            if (i < entries.size() - 1) {
                height += ROW_GAP;
            }
        }
        return height;
    }

    private int rowY(int index) {
        int y = contentY - scrollOffset;
        for (int i = 0; i < index; i++) {
            y += entries.get(i).height() + ROW_GAP;
        }
        return y;
    }

    /** 胶囊开关的位置（渲染与命中测试共用同一份几何）。 */
    private int switchX(int x, int w) {
        return x + w - 6 - SWITCH_W;
    }

    private int switchY(int y, int h) {
        return y + (h - SWITCH_H) / 2;
    }

    /** 数字行的几何：渲染与命中测试共用，避免两边算得不一样。 */
    private record CounterBox(int stepY, int autoX, int minusX, int valueX, int valueW, int plusX, boolean hasAuto) {
    }

    private CounterBox counterBox(ManageEntry.Counter entry, int x, int y, int w, int h) {
        int stepY = y + (h - STEP_H) / 2;
        int plusX = x + w - 6 - STEP_W;
        int minusX = plusX - VALUE_W - STEP_W - 4;
        // 「自动」按钮只在「当前是手动值」时出现：forced >= 0 表示手动，
        // 金币行的手动值同样 >= 0（-1 = 跟随配置），所以这个判断对两行都成立
        boolean hasAuto = entry.reset() != null && entry.forced().getAsInt() >= 0;
        int autoX = hasAuto ? minusX - 6 - AUTO_W : minusX;
        return new CounterBox(stepY, autoX, minusX, minusX + STEP_W + 2, VALUE_W, plusX, hasAuto);
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (tabsNeedRebuild) {
            tabsNeedRebuild = false;
            rebuildTabsOnly();
        }
        // 第一步固定是 super.render（它会画背景与页签控件），见 docs/ui_style.md §3
        super.render(g, mouseX, mouseY, partialTick);

        UiSurface.panel(g, panelX, panelY, panelW, panelH);
        renderHeader(g);
        renderRows(g, mouseX, mouseY);
        UiSurface.scrollbar(g, panelX + panelW - PAD - SCROLL_W, contentY, contentH,
                scrollbarThumbY(), scrollbarThumbH(), isScrollbarHovered(mouseX, mouseY) || draggingScrollbar);
        renderFooter(g);
    }

    /** 面板头：分类名（分类色）+ 行数 / 搜索词。 */
    private void renderHeader(GuiGraphics g) {
        int color = categoryColor();
        int textY = panelY + 1 + (HEADER_H - 8) / 2;
        UiText.draw(g, font, Component.translatable(labelKey(selectedCatIdx)).withStyle(ChatFormatting.BOLD),
                panelX + PAD, textY, color);
        String right = filter.isEmpty()
                ? Component.translatable("screen.game_manage.header.count", entries.size()).getString()
                : Component.translatable("screen.game_manage.header.filter", filter, entries.size()).getString();
        UiText.drawRight(g, font, UiText.literal(right), panelX + panelW - PAD, textY,
                filter.isEmpty() ? SRETheme.MUTED : SRETheme.BRASS);
        UiSurface.divider(g, panelX + 1, panelY + 1 + HEADER_H, panelW - 2);
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        for (int i = 0; i < entries.size(); i++) {
            int y = rowY(i);
            ManageEntry entry = entries.get(i);
            int h = entry.height();
            if (y + h < contentY || y > contentY + contentH) {
                continue;
            }
            renderRow(g, entry, i, y, mouseX, mouseY);
        }
        g.disableScissor();
    }

    private void renderRow(GuiGraphics g, ManageEntry entry, int index, int y, int mouseX, int mouseY) {
        int x = contentX;
        int w = contentW;
        int h = entry.height();
        boolean hovered = isInRect(mouseX, mouseY, x, y, w, h) && isInRect(mouseX, mouseY, contentX, contentY, contentW, contentH);

        if (entry instanceof ManageEntry.Label label) {
            // 分组标题：2px 分类色条 + 粗体标题 + 右侧一条淡分隔线（扁平，不铺背景条）
            int color = label.color();
            g.fill(x, y + 2, x + 2, y + h - 1, color);
            int textX = x + 7;
            UiText.draw(g, font, label.text().copy().withStyle(ChatFormatting.BOLD), textX, y + (h - 8) / 2, color);
            int lineX = textX + font.width(label.text().copy().withStyle(ChatFormatting.BOLD)) + 6;
            if (lineX < x + w) {
                UiSurface.divider(g, lineX, y + h / 2, x + w - lineX);
            }
            return;
        }
        if (entry instanceof ManageEntry.Divider) {
            UiSurface.divider(g, x + 2, y + h / 2, w - 4);
            return;
        }

        float anim = hoverAnims[Math.floorMod(index, hoverAnims.length)];
        anim = Mth.lerp(0.25F, anim, hovered ? 1F : 0F);
        hoverAnims[Math.floorMod(index, hoverAnims.length)] = anim;

        int cardH = h - 1;
        UiSurface.card(g, x, y, w, cardH, SRETheme.CARD_BG,
                hovered ? SRETheme.BRASS : SRETheme.CARD_BORDER, UiSurface.RADIUS_ROW);
        int textY = y + (cardH - 8) / 2;

        switch (entry) {
            case ManageEntry.Command command -> {
                UiText.draw(g, font, command.text(), x + 7, textY,
                        hovered ? SRETheme.BRASS_BRIGHT : SRETheme.TEXT);
                if (hovered) {
                    hoverTooltip(g, y, Component.translatable("screen.game_manage.click_to_send",
                            Component.literal("/" + command.command()).withStyle(ChatFormatting.WHITE)));
                }
            }
            case ManageEntry.Nav nav -> {
                UiText.draw(g, font, nav.text(), x + 7, textY,
                        hovered ? SRETheme.BRASS_BRIGHT : SRETheme.TEXT);
                UiText.drawRight(g, font, UiText.literal(">"), x + w - 6, textY,
                        hovered ? SRETheme.BRASS_BRIGHT : SRETheme.MUTED);
            }
            case ManageEntry.Toggle toggle -> {
                var state = toggle.state().get();
                UiText.draw(g, font, toggle.text(), x + 7, textY,
                        state.isOn() ? toggle.labelColor() : SRETheme.MUTED);
                UiSurface.switchTrack(g, switchX(x, w), switchY(y, cardH), SWITCH_W, SWITCH_H,
                        state.isOn(), state == SwitchState.ON ? 1F
                                : state == SwitchState.UNSET ? 0.5F : 0F,
                        hovered);
                if (hovered) {
                    hoverTooltip(g, y, Component.translatable("screen.game_manage.toggle_hint"));
                }
            }
            case ManageEntry.Counter counter -> {
                CounterBox box = counterBox(counter, x, y, w, cardH);
                int forced = counter.forced().getAsInt();
                String value = forced >= 0 ? String.valueOf(forced)
                        : Component.translatable("screen.game_manage.value.auto").getString();
                String unit = counter.unit() == null ? "" : counter.unit().getString();
                if (forced >= 0 && !unit.isEmpty()) {
                    value = value + " " + unit;
                }
                UiText.draw(g, font, counter.text(), x + 7, textY, counter.labelColor());
                String shown = MapUiGraphics.clip(font, value, box.valueW() - 4);
                UiText.drawCentered(g, font, UiText.literal(shown), box.valueX() + box.valueW() / 2, textY,
                        forced >= 0 ? SRETheme.TEXT : SRETheme.MUTED);
                drawStep(g, box.minusX(), box.stepY(), "-", hovered && isInRect(mouseX, mouseY, box.minusX(),
                        box.stepY(), STEP_W, STEP_H));
                drawStep(g, box.plusX(), box.stepY(), "+",
                        hovered && isInRect(mouseX, mouseY, box.plusX(), box.stepY(), STEP_W, STEP_H));
                if (box.hasAuto()) {
                    drawAuto(g, box.autoX(), box.stepY(), counter, hovered
                            && isInRect(mouseX, mouseY, box.autoX(), box.stepY(), AUTO_W, STEP_H));
                }
                if (hovered) {
                    String current = forced >= 0 ? String.valueOf(forced)
                            : Component.translatable("screen.game_manage.value.auto_estimate",
                                    counter.autoEstimate().getAsInt()).getString();
                    hoverTooltip(g, y, Component.translatable("screen.game_manage.counter_hint", current));
                }
            }
            // 分组标题与分隔线在上面已经 return
            default -> {
            }
        }
    }

    /** 「-」/「+」小方块。 */
    private void drawStep(GuiGraphics g, int x, int y, String glyph, boolean hover) {
        UiSurface.card(g, x, y, STEP_W, STEP_H, hover ? SRETheme.SURFACE_BOTTOM_ALT : SRETheme.CARD_BG,
                hover ? SRETheme.BRASS : SRETheme.CARD_BORDER, UiSurface.RADIUS_CHIP);
        UiText.drawCentered(g, font, UiText.literal(glyph), x + STEP_W / 2, y + (STEP_H - 8) / 2,
                hover ? SRETheme.BRASS_BRIGHT : SRETheme.TEXT);
    }

    /** 「自动」小按钮（只有当前是手动值时才出现）。 */
    private void drawAuto(GuiGraphics g, int x, int y, ManageEntry.Counter counter, boolean hover) {
        UiSurface.card(g, x, y, AUTO_W, STEP_H, hover ? SRETheme.SURFACE_BOTTOM_ALT : SRETheme.CARD_BG,
                hover ? SRETheme.BRASS : SRETheme.CARD_BORDER, UiSurface.RADIUS_CHIP);
        String text = MapUiGraphics.clip(font,
                Component.translatable("screen.game_manage.value.auto").getString(), AUTO_W - 4);
        UiText.drawCentered(g, font, UiText.literal(text), x + AUTO_W / 2, y + (STEP_H - 8) / 2,
                hover ? SRETheme.BRASS_BRIGHT : SRETheme.MUTED);
    }

    /** 悬停提示：贴在被悬停行的上方（顶到内容区上沿时改放到下方），并按面板宽度裁掉超长指令。 */
    private void hoverTooltip(GuiGraphics g, int rowY, Component text) {
        int y = rowY - 10;
        if (y < contentY) {
            y = rowY + 14;
        }
        String shown = MapUiGraphics.clip(font, text.getString(), contentW - 4);
        UiText.draw(g, font, Component.literal(shown), contentX + 2, y, SRETheme.BRASS_BRIGHT);
    }
    private void renderFooter(GuiGraphics g) {
        Component hint = filter.isEmpty()
                ? Component.translatable("screen.game_manage.hint")
                : Component.translatable("screen.game_manage.hint.filter");
        UiText.drawCentered(g, font, hint, this.width / 2, this.height - 16, SRETheme.MUTED);
    }

    // ══════════════════════════════════════════════════════════════════
    // 滚动条几何
    // ══════════════════════════════════════════════════════════════════

    private int scrollbarThumbH() {
        if (maxScroll <= 0) {
            return contentH;
        }
        return Math.max(SCROLL_MIN_THUMB, (int) (contentH * (float) contentH / Math.max(1, totalEntriesHeight())));
    }

    private int scrollbarThumbY() {
        if (maxScroll <= 0) {
            return contentY;
        }
        return contentY + (int) ((contentH - scrollbarThumbH()) * ((float) scrollOffset / maxScroll));
    }

    private boolean isScrollbarHovered(int mouseX, int mouseY) {
        return isInRect(mouseX, mouseY, panelX + panelW - PAD - SCROLL_W, contentY, SCROLL_W, contentH);
    }

    // ══════════════════════════════════════════════════════════════════
    // 输入
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int mouseX = (int) mx;
            int mouseY = (int) my;

            if (maxScroll > 0 && isScrollbarHovered(mouseX, mouseY)) {
                draggingScrollbar = true;
                dragStartY = my;
                dragStartOffset = scrollOffset;
                return true;
            }

            if (isInRect(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
                for (int i = 0; i < entries.size(); i++) {
                    int y = rowY(i);
                    ManageEntry entry = entries.get(i);
                    int h = entry.height();
                    if (h <= 0 || !isInRect(mouseX, mouseY, contentX, y, contentW, h)) {
                        continue;
                    }
                    return activate(entry, mouseX, mouseY, y);
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** 命中一行后要做的事（按行类型分派）。 */
    private boolean activate(ManageEntry entry, int mouseX, int mouseY, int y) {
        switch (entry) {
            case ManageEntry.Command command -> {
                click();
                command(command.command());
                return true;
            }
            case ManageEntry.Nav nav -> {
                click();
                open(nav.target());
                return true;
            }
            case ManageEntry.Toggle toggle -> {
                click();
                var state = toggle.state().get() != null ? toggle.state().get()
                        : SwitchState.UNSET;
                toggle.setter().accept(!state.isOn());
                rebuildEntries();
                return true;
            }
            case ManageEntry.Counter counter -> {
                int cardH = entry.height() - 1;
                CounterBox box = counterBox(counter, contentX, y, contentW, cardH);
                int forced = counter.forced().getAsInt();
                if (box.hasAuto() && isInRect(mouseX, mouseY, box.autoX(), box.stepY(), AUTO_W, STEP_H)) {
                    click();
                    counter.reset().run();
                    return true;
                }
                if (isInRect(mouseX, mouseY, box.minusX(), box.stepY(), STEP_W, STEP_H)) {
                    return applyCounter(counter, forced >= 0 ? forced - 1 : counter.autoEstimate().getAsInt() - 1);
                }
                if (isInRect(mouseX, mouseY, box.plusX(), box.stepY(), STEP_W, STEP_H)) {
                    return applyCounter(counter, forced >= 0 ? forced + 1 : counter.autoEstimate().getAsInt() + 1);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean applyCounter(ManageEntry.Counter counter, int value) {
        click();
        counter.apply().accept(Mth.clamp(value, counter.min(), counter.max()));
        rebuildEntries();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar && maxScroll > 0) {
            int thumbH = scrollbarThumbH();
            double trackH = Math.max(1, contentH - thumbH);
            scrollOffset = Mth.clamp((int) (dragStartOffset + (my - dragStartY) / trackH * maxScroll), 0, maxScroll);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isInRect((int) mx, (int) my, panelX, panelY, panelW, panelH)) {
            scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * (ManageEntry.ROW_H + ROW_GAP)), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 || keyCode == 262) { // ← / →
            selectCategory(selectedCatIdx + (keyCode == 263 ? -1 : 1));
            return true;
        }
        if (keyCode == 259) { // Backspace：删掉搜索词的最后一个字符
            if (!filter.isEmpty()) {
                filter = filter.substring(0, filter.length() - 1);
                rebuildEntries();
                return true;
            }
        }
        if (keyCode == 256) { // Esc：先清搜索词，再退出
            if (!filter.isEmpty()) {
                filter = "";
                rebuildEntries();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= 32 && codePoint != 127 && filter.length() < 32) {
            filter = filter + codePoint;
            rebuildEntries();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void click() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
