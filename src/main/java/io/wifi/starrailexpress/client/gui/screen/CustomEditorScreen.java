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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.gui.HintText;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.client.gui.widget.SreSwitchButton;
import io.wifi.starrailexpress.client.gui.widget.SreTabButton;
import io.wifi.starrailexpress.client.gui.widget.SwitchState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 「自定义内容编辑器」基类：面板 + 标题条 + 页签 + 可滚动字段列表 + 页脚。
 *
 * <p>
 * 自定义职业 / 修饰符 / 物品 / 方块四个界面原先各自复制了一套布局、滚动、页签、渲染与鼠标处理
 * （约 200 行 × 4 份，且已经互相分叉），这里统一收拢，子类只需要：
 * <ol>
 * <li>声明 {@link #translationPrefix()} / {@link #tabKeys()}；</li>
 * <li>在 {@link #buildTab(int)} 里用 {@link #field}、{@link #cluster}、{@link #number} 等行 API 描述字段；</li>
 * <li>实现 {@link #onSave()} 与 {@link #onOpenManage()}；</li>
 * <li>需要右侧预览的界面覆写 {@link #previewSize()} 与 {@link #renderPreviewContent}。</li>
 * </ol>
 *
 * <p>
 * 几个关键约定：
 * <ul>
 * <li><b>重建一律走 {@link #requestRebuild()}</b>：在 {@code render} 开头统一重建。页签/开关按钮的回调里
 * 直接重建会撞上控件列表的并发修改，而且会漏掉 {@code clearWidgets()}（旧实现就这么漏了，控件只增不减）；</li>
 * <li><b>只注册当前页签的控件</b>：旧实现把 5 个页签的控件都注册进事件系统，各页签同坐标控件完全重合，
 * 点击会命中别的页签看不见的输入框；</li>
 * <li><b>先 setMaxLength 再 setValue</b>：{@code EditBox} 默认上限 32，顺序反了会把初始值静默截断，
 * 下次编辑再写回就等于丢数据；</li>
 * <li>行内控件一律由 {@link EditorLayout#pack} 排布，宽度不够自动折行，绝不允许写死偏移量戳出面板。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public abstract class CustomEditorScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════
    // 文本长度上限（需求：允许输入更长的文本）
    // ══════════════════════════════════════════════════════════════════

    /** 标识符：<code>englishId</code> / <code>id</code>。 */
    public static final int LIMIT_ID = 128;
    /** 显示名、短标签。 */
    public static final int LIMIT_NAME = 256;
    /** 贴图 / 模型 / 继承物品或方块的路径 id。 */
    public static final int LIMIT_PATH = 512;
    /** 一般长文本：描述、提示行、单条指令、JSON 片段。 */
    public static final int LIMIT_TEXT = 1024;
    /** 整段指令（效果表、指令列表汇总）。 */
    public static final int LIMIT_COMMAND = 4096;
    /** 数值字段。 */
    public static final int LIMIT_NUMBER = 16;

    /** 输入框默认高度。 */
    protected static final int WIDGET_H = EditorLayout.WIDGET_H;
    /** 行内单元间距。 */
    protected static final int CELL_GAP = EditorLayout.GAP;
    /** 「是 / 否」开关按钮的宽度（只放状态符号 + 状态文字，不铺满整行）。 */
    protected static final int SWITCH_W = 64;
    /** 卡片（一组相关字段的容器）左右内缩与标题条高度。 */
    protected static final int CARD_PAD = 5;
    protected static final int CARD_HEADER_H = 18;

    // ══════════════════════════════════════════════════════════════════
    // 状态
    // ══════════════════════════════════════════════════════════════════

    private final List<Row> rows = new ArrayList<>();
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final List<AbstractWidget> tabButtons = new ArrayList<>();
    private final List<AbstractWidget> footerButtons = new ArrayList<>();
    private final List<EditBox> fieldOrder = new ArrayList<>();
    private final Map<AbstractWidget, Integer> baseY = new IdentityHashMap<>();
    /** 输入框的完整占位提示：排完宽度才知道要裁多少，所以先存下来（{@link #clipBoxHint}）。 */
    private final Map<EditBox, Component> editHints = new IdentityHashMap<>();

    private final List<TextEntry> texts = new ArrayList<>();
    private final List<int[]> separators = new ArrayList<>();

    /** 构建期的卡片栈（cardBegin / cardEnd 配对）。 */
    private final List<Card> openCards = new ArrayList<>();
    /** 排布好的卡片（渲染背景 + 描边 + 标题条用）。 */
    private final List<Card> cards = new ArrayList<>();
    /** 折叠起来的卡片 id：折叠后只剩标题条一行，用来「一次只看一块」。 */
    private final Set<String> collapsedCards = new HashSet<>();

    private EditorLayout layout;
    private int activeTab;
    private float scroll;
    private int maxScroll;
    private int contentHeight;
    private int textOffset;

    private int focusedFieldIndex = -1;
    private int focusedFieldCursor;

    private boolean pendingRebuild;
    private boolean isDraggingScroll;
    private double dragStartY;
    private float dragStartScroll;

    private int previewSlotX, previewSlotY, previewSlotSize;
    private String previewLabelKey;

    // 当前正在构建的行上下文
    private Row building;

    // ══════════════════════════════════════════════════════════════════
    // 子类接口
    // ══════════════════════════════════════════════════════════════════

    protected CustomEditorScreen(Component title) {
        super(title);
    }

    /** 翻译键前缀，例如 {@code sre.custom_role}：标题 = {@code .title}，页签 = {@code .tab.<名字>}。 */
    protected abstract String translationPrefix();

    /** 页签名字（拼接在 {@code <前缀>.tab.} 后面）。 */
    protected abstract String[] tabKeys();

    /** 实际可见的页签下标（默认全部）；子类可以按数据状态隐藏一部分。 */
    protected List<Integer> visibleTabs() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < tabKeys().length; i++) {
            all.add(i);
        }
        return all;
    }

    /** 构建指定页签的内容（调用本类的行 API）。 */
    protected abstract void buildTab(int tab);

    /** 点「保存」。 */
    protected abstract void onSave();

    /** 点「管理」。 */
    protected abstract void onOpenManage();

    /**
     * 需要右侧材质预览的界面返回预览边长（&gt;0），否则返回 0。
     *
     * <p>
     * 预览会占用内容区右侧一列；窄屏放不下时自动改成内容区里的一行由 {@link #previewRow} 预留，
     * 永远不会压在字段上。
     */
    protected int previewSize() {
        return 0;
    }

    /** 把预览内容画进给定的正方形里（基类已经画好边框与占位棋盘）。 */
    protected void renderPreviewContent(GuiGraphics g, int x, int y, int size) {
    }

    /** 哪些页签显示预览（默认凡是声明了 {@link #previewSize()} 的都显示）。 */
    protected boolean showPreview(int tab) {
        return previewSize() > 0;
    }

    /** 额外的内容区绘制（在裁剪区内、文字之后调用）。 */
    protected void renderContentExtra(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** 把输入框里的内容落盘（默认无需处理：responder 已经在每次敲键时写回数据）。 */
    protected void commitInput() {
    }

    /** 面板尺寸参数；默认 700×560 上限，标签列按文字实测。 */
    protected EditorLayout.Config layoutConfig() {
        return EditorLayout.Config.defaults();
    }

    /** 当前页签下标。 */
    protected int activeTab() {
        return activeTab;
    }

    /** 表格/滚动所在的裁剪区，供子类做额外绘制时参考。 */
    protected EditorLayout layout() {
        return layout;
    }

    // ══════════════════════════════════════════════════════════════════
    // 重建与初始化
    // ══════════════════════════════════════════════════════════════════

    /** 请求重建界面：在下一帧 {@code render} 开头统一执行。 */
    protected void requestRebuild() {
        this.pendingRebuild = true;
    }

    /** 回到顶部（内容整体换了才用，例如切换子类型）。 */
    protected void resetScroll() {
        this.scroll = 0;
    }

    /**
     * 重建。必须走 {@code clearWidgets()}：旧实现直接调 {@code init(minecraft,width,height)}，
     * 控件会一遍遍重复注册进事件系统，焦点也会落到已经被清掉的控件上。
     */
    private void rebuild() {
        rememberFocus();
        clearWidgets();
        setFocused(null);
        init();
        restoreFocus();
    }

    private void rememberFocus() {
        focusedFieldIndex = -1;
        focusedFieldCursor = 0;
        if (getFocused() instanceof EditBox box) {
            focusedFieldIndex = fieldOrder.indexOf(box);
            focusedFieldCursor = box.getCursorPosition();
        }
    }

    private void restoreFocus() {
        if (focusedFieldIndex < 0 || fieldOrder.isEmpty()) {
            return;
        }
        EditBox box = fieldOrder.get(Math.min(focusedFieldIndex, fieldOrder.size() - 1));
        setFocused(box);
        box.setCursorPosition(Math.min(focusedFieldCursor, box.getValue().length()));
        scrollIntoView(box);
    }

    @Override
    protected void init() {
        rows.clear();
        contentWidgets.clear();
        tabButtons.clear();
        footerButtons.clear();
        fieldOrder.clear();
        baseY.clear();
        editHints.clear();
        texts.clear();
        separators.clear();
        openCards.clear();
        cards.clear();
        previewSlotSize = 0;
        previewLabelKey = null;
        building = null;

        List<Integer> visible = visibleTabs();
        if (!visible.contains(activeTab)) {
            activeTab = visible.isEmpty() ? 0 : visible.get(0);
            scroll = 0;
        }
        String[] keys = tabKeys();
        int[] tabWidths = new int[visible.size()];
        for (int slot = 0; slot < visible.size(); slot++) {
            String key = keys[visible.get(slot)];
            Component plain = Component.translatable(translationPrefix() + ".tab." + key);
            Component bold = plain.copy().withStyle(style -> style.withBold(true));
            tabWidths[slot] = Math.max(font.width(plain), font.width(bold)) + 16;
        }

        // 先按顺序构建内容（只登记行，不算坐标），再据此定标签列宽与整屏布局
        buildTab(activeTab);
        mergeSwitchRows();

        int maxLabelWidth = 0;
        for (Row row : rows) {
            if (row.labelKey != null) {
                maxLabelWidth = Math.max(maxLabelWidth, font.width(Component.translatable(row.labelKey)));
            }
        }

        EditorLayout.Config cfg = layoutConfig();
        if (previewSize() > 0 && showPreview(activeTab)) {
            cfg = cfg.previewColumn(previewSize() + 8);
        }
        this.layout = EditorLayout.of(width, height, cfg, tabWidths, maxLabelWidth);

        buildTabBar();
        buildFooter();
        flushRows();

        contentHeight = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + rows.get(rows.size() - 1).height
                - layout.contentY();
        maxScroll = EditorLayout.maxScroll(contentHeight, layout.contentH());
        scroll = clampScroll(scroll);
        applyScroll();
    }

    private int clampScroll(float value) {
        return Math.round(Math.max(0F, Math.min(maxScroll, value)));
    }

    /** 子类在 {@link #buildTab(int)} 里为预览预留一行；宽屏会折叠成 0 高并由右侧预览列接管。 */
    protected int previewRow(int r, String labelKey) {
        Row row = newRow(EditorLayout.RowKind.ROW);
        row.previewRow = true;
        row.labelKey = labelKey;
        rows.add(row);
        this.previewLabelKey = labelKey;
        return r + 1;
    }

    // ══════════════════════════════════════════════════════════════════
    // 行 API（全部返回「下一行号」，方便 r = xxx(r, ...) 的写法）
    // ══════════════════════════════════════════════════════════════════

    /** 一行：标签 + 撑满字段区的输入框。 */
    protected int field(int r, String labelKey, String value, int maxLength, Component hint,
            Consumer<String> setter) {
        return row(r, labelKey, box(value, maxLength, hint, setter));
    }

    /** 一行：标签 + 定宽输入框（数值字段用）。 */
    protected int number(int r, String labelKey, String value, String unitKey, Consumer<String> setter) {
        return number(r, labelKey, value, unitKey, 90, setter);
    }

    /** 一行：标签 + 定宽输入框 + 单位提示。 */
    protected int number(int r, String labelKey, String value, String unitKey, int width,
            Consumer<String> setter) {
        return row(r, labelKey, fixedBox(value, LIMIT_NUMBER, width, null, setter), tag(unitKey));
    }

    /** 一行：标签 + 三色 RGB 数字框（0~255）。 */
    protected int rgb(int r, String labelKey, String hintKey, int[] rgb, Consumer<int[]> setter) {
        Component hint = hintKey == null ? null : Component.translatable(hintKey);
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final int channel = i;
            cells.add(fixedBox(String.valueOf(rgb[i]), LIMIT_NUMBER, 48, hint, value -> {
                try {
                    int parsed = Integer.parseInt(value.trim());
                    int[] next = rgb.clone();
                    next[channel] = Math.max(0, Math.min(255, parsed));
                    setter.accept(next);
                } catch (NumberFormatException ignored) {
                    // 输入中途（空串、负号）不覆盖原值
                }
            }));
        }
        return cluster(r, labelKey, cells.toArray(Cell[]::new));
    }

    /** 一行：标签 + 是/否 开关按钮。切换不重建界面（焦点与滚动位置都保留）。 */
    protected int toggle(int r, String labelKey, boolean current, Consumer<Boolean> setter) {
        return toggle(r, labelKey, current, setter, false);
    }

    /**
     * 一行：一个「开关按钮」，按钮文字就是这一项的标题，右端是带颜色的状态符号。
     *
     * <p>
     * 不加标签列（按钮自带标题）：这样一行就只有一个可点区域，
     * 不会出现「左边一个标签、右边按钮上又写一遍」的重复。
     *
     * @param rebuild 切换后是否要重建（只有会影响「显示哪些字段」的开关才需要）
     */
    protected int toggle(int r, String labelKey, boolean current, Consumer<Boolean> setter, boolean rebuild) {
        return cluster(r, null, toggleCell(labelKey, current, setter, rebuild));
    }

    /** 可放进 {@link #cluster} 的开关按钮（一行并排两个开关时用）。 */
    protected Cell toggleCell(String labelKey, boolean current, Consumer<Boolean> setter, boolean rebuild) {
        SwitchState[] state = { SwitchState.of(current) };
        SreSwitchButton button = new SreSwitchButton(font, Component.translatable(labelKey), () -> state[0], null, b -> {
            // 两态开关：开 ⇄ 关，不会跳到「未设置」
            state[0] = state[0].toggled();
            setter.accept(state[0].isOn());
            if (rebuild) {
                requestRebuild();
            }
        });
        return switchCell(button, 120, 1F);
    }

    /**
     * 定宽的「是 / 否」开关：标签画在标签列上，按钮里只放状态符号 + 状态文字。
     *
     * <p>
     * <b>不参与自动并排</b>（{@code mergeable} 为 false）：这种行由「左侧普通文本 + 右侧小方块」组成，
     * 并排后一行里会出现两组「文本 + 方块」，很乱；一行一个更清楚。
     *
     * @param stateText 状态文字（例如「是」「否」），由子类给翻译键；为 null 则只显示符号
     */
    protected Cell yesNoSwitchCell(boolean current, java.util.function.Function<Boolean, Component> stateText,
            Consumer<Boolean> setter, boolean rebuild) {
        SwitchState[] state = { SwitchState.of(current) };
        SreSwitchButton button = new SreSwitchButton(font, null, () -> state[0],
                s -> stateText.apply(s.isOn()), b -> {
                    state[0] = state[0].toggled();
                    setter.accept(state[0].isOn());
                    if (rebuild) {
                        requestRebuild();
                    }
                });
        return cell(button, SWITCH_W, SWITCH_W, 0F);
    }

    /** 三态开关（是 / 否 / 未设置）：未设置画成土褐色的「–」，和「否」区分开。 */
    protected Cell triSwitchCell(String labelKey, Boolean current, Consumer<Boolean> setter, boolean rebuild) {
        SwitchState[] state = { SwitchState.of(current) };
        SreSwitchButton button = new SreSwitchButton(font, Component.translatable(labelKey), () -> state[0], null, b -> {
            state[0] = state[0].next();
            setter.accept(state[0].toBoolean());
            if (rebuild) {
                requestRebuild();
            }
        });
        return switchCell(button, 120, 1F);
    }

    /**
     * 三态开关单元（自带标题 + <b>写出状态</b>）：点一下在「未设置 → 开 → 关 → 未设置」之间循环。
     *
     * <p>
     * 适合「一个类别一个按钮、三种状态都要看得懂」的场景（例如自定义修饰符的阵营限制：
     * 不限 / 仅给该阵营刷新 / 不给该阵营刷新）。比并排两个「✓ / ✗」按钮舒服得多：标题只出现一次、
     * 状态写成文字，而且天然不会出现两个方向同时生效的矛盾状态。
     *
     * <p>
     * 这种单元自带标题，所以会和相邻的同类行自动并排（见 {@link #mergeSwitchRows}），宽度按一行里
     * 最宽的标题 + 状态词取齐。
     *
     * @param stateText 状态词；收 {@link SwitchState}，三种状态都要处理（写 switch 时编译器会强制）
     */
    protected Cell triStateCell(Component title, SwitchState current,
            java.util.function.Function<SwitchState, Component> stateText, Consumer<SwitchState> setter,
            boolean rebuild) {
        SwitchState[] state = { current };
        SreSwitchButton button = new SreSwitchButton(font, title, () -> state[0], stateText, b -> {
            state[0] = state[0].next();
            setter.accept(state[0]);
            if (rebuild) {
                requestRebuild();
            }
        });
        return switchCell(button, 120, 1F);
    }

    /** 开关按钮单元：额外标记「可与相邻开关行并排」。 */
    private Cell switchCell(AbstractWidget button, int minWidth, float weight) {
        Cell cell = cell(button, minWidth, 0, weight);
        cell.mergeable = true;
        return cell;
    }

    /** 一行：标签 + 轮回按钮（每次点击切到枚举的下一个值）。 */
    protected int choice(int r, String labelKey, String keyPrefix, Enum<?> current, Consumer<Integer> setter) {
        return choice(r, labelKey, keyPrefix, current, setter, false);
    }

    protected int choice(int r, String labelKey, String keyPrefix, Enum<?> current, Consumer<Integer> setter,
            boolean rebuild) {
        Object[] values = current.getDeclaringClass().getEnumConstants();
        int[] index = { current.ordinal() };
        SreButton button = new SreButton(font, choiceLabel(keyPrefix, current.name()), b -> {
            index[0] = (index[0] + 1) % values.length;
            setter.accept(index[0]);
            b.setMessage(choiceLabel(keyPrefix, ((Enum<?>) values[index[0]]).name()));
            if (rebuild) {
                requestRebuild();
            }
        });
        return row(r, labelKey, cell(button, 120, 0, 1F));
    }

    /**
     * 一行：标签 + 一个按钮，按钮文字由调用方用 {@code textSupplier} 提供、点击后就地刷新
     * （不重建界面，所以切开关不会丢焦点、也不会跳回顶部）。
     */
    protected int stateButton(int r, String labelKey, java.util.function.Supplier<Component> textSupplier,
            Runnable onClick) {
        return stateButton(r, labelKey, textSupplier, onClick, false);
    }

    protected int stateButton(int r, String labelKey, java.util.function.Supplier<Component> textSupplier,
            Runnable onClick, boolean rebuild) {
        SreButton button = new SreButton(font, textSupplier.get(), b -> {
            onClick.run();
            b.setMessage(textSupplier.get());
            if (rebuild) {
                requestRebuild();
            }
        });
        return row(r, labelKey, cell(button, 120, 0, 1F));
    }

    /** 一行：标签 + 若干自定义单元（输入框 / 按钮 / 单位文字），放不下自动折行。 */
    protected int cluster(int r, String labelKey, Cell... cells) {
        Row row = newRow(EditorLayout.RowKind.ROW);
        row.labelKey = labelKey;
        for (Cell cell : cells) {
            row.cells.add(cell);
        }
        rows.add(row);
        return r + 1;
    }

    /**
     * 把相邻的「纯标题开关行」并成一行。
     *
     * <p>
     * 一行只放一个开关按钮太浪费纵向空间（面板本来就不高）：连续几个<b>按钮自带标题</b>的开关行
     * （{@link #toggleCell} / {@link #triSwitchCell}）会在排布时自动并排；并排后这一行放不下的话，
     * {@link EditorLayout#pack} 会照旧折回多行，所以窄面板下等同于原来的逐行排列，不会挤坏。
     *
     * <p>
     * <b>只有这种「整行就是一个按钮」的行才并排</b>：像「标签 + 是/否 小方块」那种行（{@link #yesNoSwitchCell}）
     * 左边是普通文本、右边是小方块，并排之后一行里会出现两组「文本 + 方块」，读起来很乱，
     * 所以它们永远一行一个。
     *
     * <p>
     * 并排后每个按钮的宽度按<b>这一行里最宽的那个按钮的标题</b>实测（不再各自拉伸成半行），
     * 于是短标题不会变成一大条空按钮；没并排的单行按钮照旧占满整行（长条更好点）。
     */
    private void mergeSwitchRows() {
        int i = 0;
        while (i < rows.size()) {
            Row current = rows.get(i);
            if (!isMergeableSwitchRow(current)) {
                i++;
                continue;
            }
            Row next = i + 1 < rows.size() ? rows.get(i + 1) : null;
            if (next == null || !isMergeableSwitchRow(next)
                    || !java.util.Objects.equals(current.cardId, next.cardId)) {
                i++;
                continue;
            }
            current.cells.addAll(next.cells);
            rows.remove(i + 1);
            // 并完继续看后面还有没有能并进来的
        }

        // 真的并排了的行：宽度按行内最宽的标题取齐（单行的长按钮保持满宽，不动）
        for (Row row : rows) {
            if (row.cells.size() < 2 || !isMergeableSwitchRow(row)) {
                continue;
            }
            int widest = 0;
            for (Cell cell : row.cells) {
                widest = Math.max(widest, SreSwitchButton.naturalWidth(font, cell.widget));
            }
            if (widest <= 0) {
                continue;
            }
            for (Cell cell : row.cells) {
                cell.minW = widest;
                cell.maxW = widest;
                cell.weight = 0F;
            }
        }
    }

    /** 这一行是不是「整行就是一个（或多个）自带标题的开关按钮」的行。 */
    private static boolean isMergeableSwitchRow(Row row) {
        if (row.kind != EditorLayout.RowKind.ROW || row.cardHeader || row.previewRow) {
            return false;
        }
        if (row.labelKey != null || row.cells.isEmpty()) {
            return false;
        }
        for (Cell cell : row.cells) {
            // 混了输入框 / 普通按钮 / 行内标签的行不参与并排
            if (!cell.mergeable) {
                return false;
            }
        }
        return true;
    }

    /** 一行：只有标签（分组用）。 */
    protected int labelRow(int r, String labelKey) {
        Row row = newRow(EditorLayout.RowKind.ROW);
        row.labelKey = labelKey;
        rows.add(row);
        return r + 1;
    }

    /** 小节标题：金色粗体 + 一条分隔线。 */
    protected int section(int r, String key) {
        Row row = newRow(EditorLayout.RowKind.SECTION);
        row.labelKey = key;
        rows.add(row);
        return r + 1;
    }

    /** 整段说明：按内容宽度折行，最多 {@code maxLines} 行，再放不下靠悬停看全文。 */
    protected int note(int r, String key, int color) {
        return note(r, key, color, 2);
    }

    protected int note(int r, String key, int color, int maxLines) {
        if (key == null) {
            return r;
        }
        return note(r, Component.translatable(key), color, maxLines);
    }

    /** 整段说明（带参数格式化好的文案直接传 Component）。 */
    protected int note(int r, Component text, int color) {
        return note(r, text, color, 2);
    }

    protected int note(int r, Component text, int color, int maxLines) {
        if (text == null) {
            return r;
        }
        Row row = newRow(EditorLayout.RowKind.NOTE);
        row.note = text;
        row.noteColor = color;
        row.noteMaxLines = Math.max(1, maxLines);
        rows.add(row);
        return r + 1;
    }

    /** 留白。 */
    protected int gap(int r) {
        rows.add(newRow(EditorLayout.RowKind.GAP));
        return r + 1;
    }

    /**
     * 多行文本列表：每行一个输入框 + × 删除，末尾 ＋ 添加一行。
     *
     * <p>
     * 列表为空时会先补一行空输入框，保证界面上总有能打字的地方。
     */
    protected int lines(int r, String labelKey, List<String> list, int limit, String hintKey, String addKey,
            int max) {
        return lines(r, labelKey, list, limit, hintKey, null, addKey, translationPrefix() + ".remove", max);
    }

    /**
     * @param lineHintKey 每一行输入框自己的占位提示（列表很长时每行都提示一下该填什么）
     * @param removeKey   每行「×」按钮的文案键
     */
    protected int lines(int r, String labelKey, List<String> list, int limit, String hintKey, String lineHintKey,
            String addKey, String removeKey, int max) {
        r = labelRow(r, labelKey);
        if (hintKey != null) {
            r = note(r, hintKey, SREPanelStyle.MUTED);
        }
        if (list.isEmpty()) {
            list.add("");
        }
        Component lineHint = hint(lineHintKey);
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            r = cluster(r, null,
                    box(list.get(i), limit, lineHint, value -> list.set(index, value)),
                    removeButton(Component.translatable(removeKey), () -> {
                        list.remove(index);
                        requestRebuild();
                    }));
        }
        if (list.size() < max) {
            r = cluster(r, null, fixedButton(Component.translatable(addKey), 160,
                    () -> {
                        list.add("");
                        requestRebuild();
                    }));
        }
        return r;
    }

    private static Component hint(String key) {
        return key == null || key.isEmpty() ? null : Component.translatable(key);
    }

    // ══════════════════════════════════════════════════════════════════
    // 单元工厂
    // ══════════════════════════════════════════════════════════════════

    /** 弹性输入框（默认最小宽 90）。 */
    protected Cell box(String value, int maxLength, Component hint, Consumer<String> setter) {
        return box(value, maxLength, 90, hint, setter);
    }

    protected Cell box(String value, int maxLength, int minWidth, Component hint, Consumer<String> setter) {
        return cell(editBox(value, maxLength, minWidth, hint, setter), minWidth, 0, 1F);
    }

    /** 定宽输入框。 */
    protected Cell fixedBox(String value, int maxLength, int width, Component hint, Consumer<String> setter) {
        return cell(editBox(value, maxLength, width, hint, setter), width, width, 0F);
    }

    /** 弹性按钮，宽度按文字实测兜底，永不放不下就截断。 */
    protected Cell button(Component text, Runnable onClick) {
        int minWidth = Math.max(60, font.width(text) + 12);
        return cell(new SreButton(font, text, b -> onClick.run()), minWidth, 0, 1F);
    }

    /** 定宽按钮。 */
    protected Cell fixedButton(Component text, int width, Runnable onClick) {
        return cell(new SreButton(font, text, b -> onClick.run()), width, width, 0F);
    }

    /** 可放进 {@link #cluster} 的「点一下就换文字」的按钮。 */
    protected Cell stateButtonCell(java.util.function.Supplier<Component> textSupplier, Runnable onClick,
            boolean rebuild) {
        SreButton button = new SreButton(font, textSupplier.get(), b -> {
            onClick.run();
            b.setMessage(textSupplier.get());
            if (rebuild) {
                requestRebuild();
            }
        });
        return switchCell(button, 120, 1F);
    }

    /** 删除按钮（默认 22×18）。 */
    protected Cell removeButton(Component text, Runnable onClick) {
        return fixedButton(text, 22, onClick);
    }

    /** 行内单位/说明文字。 */
    protected Cell tag(String key) {
        return key == null ? tag(Component.empty(), SREPanelStyle.MUTED) : tag(Component.translatable(key));
    }

    protected Cell tag(Component text) {
        return tag(text, SREPanelStyle.MUTED);
    }

    protected Cell tag(Component text, int color) {
        int width = Math.max(12, font.width(text));
        return new Cell(null, text, color, width, width, 0F);
    }

    /** 直接包一个自建控件（需要特殊控件时用）。 */
    protected Cell cell(AbstractWidget widget, int minWidth, int maxWidth, float weight) {
        addWidget(widget);
        if (widget instanceof EditBox box) {
            fieldOrder.add(box);
        }
        return new Cell(widget, null, 0, minWidth, maxWidth, weight);
    }

    /**
     * 建输入框。
     *
     * <p>
     * <b>先 {@code setMaxLength} 再 {@code setValue}</b>：顺序反了，超过原版默认上限 32 的初始值会被
     * 静默截断，用户一编辑就把截断后的内容写回数据里。
     */
    protected EditBox editBox(String value, int maxLength, int width, Component hint, Consumer<String> setter) {
        EditBox box = new EditBox(font, 0, 0, width, WIDGET_H, Component.empty());
        box.setMaxLength(Math.max(1, maxLength));
        box.setValue(value == null ? "" : value);
        box.setTextColor(SREPanelStyle.TEXT);
        box.setTextColorUneditable(SREPanelStyle.MUTED);
        if (setter != null) {
            box.setResponder(setter);
        }
        if (hint != null) {
            // 占位提示用 HINT 色：原版 EditBox 用输入框的文字色画 hint，不套样式会和真实内容同色
            box.setHint(SREPanelStyle.hint(hint));
            // 输入框里画不下完整提示：悬停看全文
            box.setTooltip(Tooltip.create(hint));
            // 宽度是 pack 之后才定的，所以先记着全文，排完再按最终宽度裁（见 clipBoxHint）
            editHints.put(box, hint);
        }
        return box;
    }

    private static Component choiceLabel(String keyPrefix, String name) {
        return Component.translatable(keyPrefix + "." + name.toLowerCase(java.util.Locale.ROOT));
    }

    // ══════════════════════════════════════════════════════════════════
    // 排布
    // ══════════════════════════════════════════════════════════════════

    private int row(int r, String labelKey, Cell... cells) {
        Row row = newRow(EditorLayout.RowKind.ROW);
        row.labelKey = labelKey;
        for (Cell cell : cells) {
            row.cells.add(cell);
        }
        rows.add(row);
        return r + 1;
    }

    /**
     * 建一行：自动打上「当前所在卡片」的标记（卡片里的行会左右内缩、折叠时整块隐藏）。
     */
    private Row newRow(EditorLayout.RowKind kind) {
        Row row = new Row(kind);
        row.cardId = openCards.isEmpty() ? null : openCards.get(openCards.size() - 1).id;
        return row;
    }

    /**
     * 开一张卡片：把一组相关字段（一个事件 / 一个触发组 / 一个技能模块）框起来。
     *
     * <p>
     * 卡片头是它的标题（点一下折叠/展开），右侧可以带一个「×」删除按钮。标题条 + 描边 + 左右内缩
     * 让每块内容一眼能分清边界；折叠后只剩标题条一行，于是又能「一次只编辑一块」。
     *
     * @param cardId  折叠状态的键：同一块内容在重建前后要用同一个 id（例如 {@code "block_event_0"}）
     * @param title   卡片标题（例如「事件 1」）
     * @param badge   标题右边的小字（例如事件类型 / 条件数量），可为 null
     * @param onDelete 删除这一块的操作，可为 null（不显示删除按钮）
     */
    protected int cardBegin(int r, String cardId, Component title, Component badge, Runnable onDelete) {
        Card card = new Card(cardId, title, badge, onDelete);
        openCards.add(card);

        Row header = newRow(EditorLayout.RowKind.ROW);
        card.startRowIndex = rows.size();
        header.cardHeader = true;
        header.card = card;
        // 整条标题栏就是一个控件：标题、折叠标记、右侧的「×」都由它自己画，
        // 于是 hover 高亮能盖住整行（含 ×），不会出现「× 夹在框和高亮中间」
        header.cells.add(cell(new CardHeader(card), 120, 0, 1F));
        rows.add(header);
        return r + 1;
    }

    /** 收尾一张卡片（与 {@link #cardBegin} 配对）。 */
    protected int cardEnd(int r) {
        if (openCards.isEmpty()) {
            return r;
        }
        Card card = openCards.remove(openCards.size() - 1);
        card.endRowIndex = rows.size();
        return r + 1;
    }

    /** 把登记好的行落到真实坐标：行内控件按最小宽度装箱，放不下就折行。 */
    private void flushRows() {
        // 先全部隐藏：只有真正排进布局的控件才可见、才接受点击
        for (Row row : rows) {
            for (Cell cell : row.cells) {
                if (cell.widget != null) {
                    cell.widget.visible = false;
                }
            }
        }

        int cursor = layout.contentY();
        Card openCard = null;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            row.y = cursor;

            // 折叠的卡片：里面的行整块收起（不占高度、不排控件），只留标题条那一行
            if (row.cardId != null && !row.cardHeader && collapsedCards.contains(row.cardId)) {
                row.height = 0;
                continue;
            }
            // 卡片头：记下卡片的位置，等排完这一块再补高度
            if (row.cardHeader && row.card != null) {
                openCard = row.card;
                // 卡片框正好包住自己的行：多画几像素的话相邻卡片会互相叠
                openCard.x = layout.contentX() + 2;
                openCard.w = Math.max(60, layout.contentRight() - layout.contentX() - 4);
                openCard.y = cursor;
                openCard.headerH = EditorLayout.ROW_H;
            }

            if (row.previewRow) {
                // 宽屏：预览在右侧专属列里，这一行折叠成 0 高；窄屏：在这里占一行，由 renderPreview 居中画出
                row.height = layout.previewW() > 0 ? 0 : previewSize() + EditorLayout.LABEL_LINE_H + 6;
                if (row.height > 0) {
                    previewSlotX = layout.contentX() + Math.max(0, (layout.textW() - previewSize()) / 2);
                    previewSlotY = row.y + EditorLayout.LABEL_LINE_H;
                    previewSlotSize = previewSize();
                    if (row.labelKey != null) {
                        texts.add(new TextEntry(Component.translatable(row.labelKey), layout.contentX(),
                                row.y, layout.textW(), 1, false, SREPanelStyle.TEXT));
                    }
                }
                cursor += row.height;
                continue;
            }
            switch (row.kind) {
                case GAP -> row.height = EditorLayout.rowHeight(EditorLayout.RowKind.GAP, false, 1, 1);
                case SECTION -> {
                    int pad = row.cardId != null ? CARD_PAD : 0;
                    row.height = EditorLayout.rowHeight(EditorLayout.RowKind.SECTION, false, 1, 1);
                    texts.add(new TextEntry(Component.translatable(row.labelKey), layout.contentX() + pad,
                            row.y + 2, layout.textW() - pad * 2, 1, true, SREPanelStyle.GOLD));
                    separators.add(new int[] { row.y + row.height - 4, layout.contentX() + pad,
                            layout.textW() - pad * 2 });
                }
                case NOTE -> {
                    int pad = row.cardId != null ? CARD_PAD : 0;
                    int noteWidth = Math.max(20, layout.textW() - pad * 2);
                    int measured = Math.max(1, font.split(row.note, noteWidth).size());
                    row.height = EditorLayout.rowHeight(EditorLayout.RowKind.NOTE, false, 1,
                            Math.min(measured, row.noteMaxLines));
                    texts.add(new TextEntry(row.note, layout.contentX() + pad, row.y + 1, noteWidth,
                            row.noteMaxLines, false, row.noteColor));
                }
                case ROW -> row.height = flushRow(row);
            }
            cursor += row.height;

            // 这一行是卡片的最后一行：卡片高 = 卡片底 → 这里
            if (openCard != null && row.cardId != null && row.cardId.equals(openCard.id)
                    && openCard.endRowIndex > 0 && rowIndex == openCard.endRowIndex - 1) {
                openCard.h = cursor - openCard.y;
                cards.add(openCard);
                openCard = null;
            }
        }

        // 有控件这一轮没被排进布局（例如所在卡片被折叠了）：把焦点从它身上摘掉，
        // 否则键盘输入会继续喂给一个看不见的输入框
        if (getFocused() instanceof AbstractWidget focused && !focused.visible) {
            setFocused(null);
        }

        // 宽屏：预览贴内容区右上角（不随内容滚动），标签画在预览下面
        if (layout.previewW() > 0 && previewSize() > 0) {
            previewSlotSize = Math.min(previewSize(), Math.max(0, layout.previewH()));
            previewSlotX = layout.previewX();
            previewSlotY = layout.previewY();
        }
    }

    private int flushRow(Row row) {
        boolean fieldArea = !layout.compact();
        // 卡片里的行左右各内缩一点，卡片框才看得出来是「装着一组字段」
        int pad = row.cardId != null && !row.cardHeader ? CARD_PAD : 0;
        // 卡片头占满整行（含标签列与右侧删除按钮那一段）：它只是一条标题栏，不该被标签列顶到中间
        int rowLeft = row.cardHeader ? layout.contentX() + 2
                : (fieldArea ? layout.fieldX() : layout.contentX()) + pad;
        int rowWidth = row.cardHeader ? Math.max(40, layout.contentRight() - layout.contentX() - 4)
                : (fieldArea ? layout.fieldW() : layout.textW()) - pad * 2;

        List<Cell> cells = row.cells;
        int count = cells.size();
        int[] minW = new int[count];
        int[] maxW = new int[count];
        float[] weights = new float[count];
        for (int i = 0; i < count; i++) {
            Cell cell = cells.get(i);
            minW[i] = cell.minW;
            maxW[i] = cell.maxW;
            weights[i] = cell.weight;
        }

        int bands = 1;
        if (count > 0) {
            List<EditorLayout.CellBox> boxes = EditorLayout.pack(rowWidth, CELL_GAP, minW, maxW, weights);
            for (EditorLayout.CellBox box : boxes) {
                Cell cell = cells.get(box.index());
                cell.x = rowLeft + box.x();
                cell.width = box.width();
                cell.band = box.row();
                bands = Math.max(bands, box.row() + 1);
            }
        }

        boolean labelAbove = layout.compact();
        int height = EditorLayout.rowHeight(EditorLayout.RowKind.ROW, labelAbove, bands, 1);
        int widgetOffset = EditorLayout.widgetOffsetY(EditorLayout.RowKind.ROW, labelAbove);

        for (Cell cell : cells) {
            int y = row.y + widgetOffset + cell.band * EditorLayout.ROW_H;
            if (cell.widget != null) {
                cell.widget.setX(cell.x);
                cell.widget.setY(y);
                cell.widget.setWidth(cell.width);
                cell.widget.visible = true;
                // 宽度定下来才知道文字放不放得下：放不下就挂上「悬停看全文」
                if (cell.widget instanceof SreButton button) {
                    button.refreshTooltip();
                } else if (cell.widget instanceof EditBox box) {
                    clipBoxHint(box, cell.width);
                }
                baseY.put(cell.widget, y);
                contentWidgets.add(cell.widget);
            } else {
                texts.add(new TextEntry(cell.text, cell.x, y + (WIDGET_H - EditorLayout.LINE_H) / 2,
                        cell.width, 1, false, cell.textColor));
            }
        }

        if (row.labelKey != null) {
            int labelY = labelAbove ? row.y : row.y + EditorLayout.labelOffsetY(false);
            int labelWidth = (labelAbove ? layout.textW() : layout.labelW()) - pad;
            texts.add(new TextEntry(Component.translatable(row.labelKey), layout.contentX() + pad, labelY,
                    labelWidth, 1, false, SREPanelStyle.TEXT));
        }
        return height;
    }

    /**
     * 输入框的占位提示按<b>最终宽度</b>裁一次（多余的省略号，悬停看全文）。
     *
     * <p>
     * 原版 {@code EditBox} 画 hint 时完全不裁剪，长提示会直接溢出输入框、盖住右边的文字；
     * 而控件的最终宽度要等 {@link EditorLayout#pack} 排完才知道，所以只能在排布之后回填。
     */
    private void clipBoxHint(EditBox box, int width) {
        Component full = editHints.get(box);
        if (full == null) {
            return;
        }
        String text = full.getString();
        int room = Math.max(8, width - 8);
        if (font.width(text) > room) {
            box.setHint(SREPanelStyle.hint(Component.literal(MapUiGraphics.clip(font, text, room))));
        }
    }

    private void applyScroll() {
        int offset = Math.round(scroll);
        textOffset = offset;
        int top = layout.contentY();
        int bottom = layout.contentBottom();
        for (Map.Entry<AbstractWidget, Integer> entry : baseY.entrySet()) {
            AbstractWidget widget = entry.getKey();
            int y = entry.getValue() - offset;
            widget.setY(y);
            boolean inView = y + widget.getHeight() > top && y < bottom;
            widget.visible = inView;
            if (!inView && getFocused() == widget) {
                setFocused(null);
            }
        }
    }

    private void scrollBy(float delta) {
        float next = clampScroll(scroll + delta);
        if (next != scroll) {
            scroll = next;
            applyScroll();
        }
    }

    /** 把控件完整滚进视口（被裁掉一半的字段点一下也能整理好）。 */
    private void scrollIntoView(AbstractWidget widget) {
        Integer base = baseY.get(widget);
        if (base == null) {
            return;
        }
        int top = layout.contentY();
        int bottom = layout.contentBottom();
        float next = scroll;
        if (base - next < top) {
            next = base - top;
        } else if (base + widget.getHeight() - next > bottom) {
            next = base + widget.getHeight() - bottom;
        }
        next = clampScroll(next);
        if (Math.round(next) != Math.round(scroll)) {
            scroll = next;
            applyScroll();
        }
    }

    /** 让某个输入框拿到焦点（子类想开局就定位到某个字段时用）。 */
    protected void focus(EditBox box) {
        setFocused(box);
        scrollIntoView(box);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页签栏与页脚
    // ══════════════════════════════════════════════════════════════════

    private void buildTabBar() {
        tabButtons.clear();
        List<EditorLayout.TabSlot> slots = layout.tabs();
        List<Integer> visible = visibleTabs();
        for (int slot = 0; slot < slots.size() && slot < visible.size(); slot++) {
            final int index = visible.get(slot);
            EditorLayout.TabSlot place = slots.get(slot);
            SreTabButton button = new SreTabButton(font, place.x(), place.y(), place.w(), tabLabel(index),
                    index == activeTab, () -> {
                        if (activeTab == index) {
                            return;
                        }
                        activeTab = index;
                        scroll = 0;
                        requestRebuild();
                    });
            addWidget(button);
            tabButtons.add(button);
        }
    }

    private void buildFooter() {
        int gap = 8;
        int bw = Math.min(100, Math.max(40, (layout.panelW() - EditorLayout.PAD * 2 - gap * 2) / 3));
        int by = layout.panelY() + layout.panelH() - 26;
        int total = bw * 3 + gap * 2;
        int sx = layout.panelX() + (layout.panelW() - total) / 2;

        // 也用可裁剪按钮：面板窄或译文长时，文字裁成省略号并给悬停全文，不会压到相邻按钮上
        List<SreButton> buttons = new ArrayList<>();
        buttons.add(new SreButton(font, Component.translatable(translationPrefix() + ".save"), b -> onSave()));
        buttons.add(new SreButton(font, Component.translatable(translationPrefix() + ".manage"),
                b -> onOpenManage()));
        buttons.add(new SreButton(font, Component.translatable(translationPrefix() + ".cancel"), b -> onClose()));
        for (int i = 0; i < buttons.size(); i++) {
            SreButton button = buttons.get(i);
            button.setX(sx + i * (bw + gap));
            button.setY(by);
            button.setWidth(bw);
            button.setHeight(20);
            button.refreshTooltip();
            addWidget(button);
            footerButtons.add(button);
        }
    }

    /**
     * 文字被裁掉时挂一个「悬停看全文」的 tooltip，放得下就摘掉。
     *
     * <p>
     * 只在状态变化时动 tooltip，避免每帧新建对象。
     */
    private static void applyClipTooltip(AbstractWidget widget, boolean fits, String full, boolean shown) {
        if (fits == !shown) {
            return;
        }
        widget.setTooltip(fits ? null : Tooltip.create(Component.literal(full)));
    }

    /** 页签文字：活跃页签金色粗体，其余土褐（文档 §5 的文字层级）。 */
    private Component tabLabel(int index) {
        Component label = Component.translatable(translationPrefix() + ".tab." + tabKeys()[index]);
        if (index == activeTab) {
            return label.copy().withStyle(style -> style.withBold(true).withColor(SREPanelStyle.GOLD));
        }
        return label.copy().withStyle(style -> style.withColor(SREPanelStyle.MUTED));
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染（顺序遵循 docs/ui_style.md §3）
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 重建放在这一帧最前面，且不 return：return 会让这一帧什么都不画，切页签会闪一下
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }

        renderBackground(g, mouseX, mouseY, partialTick);

        for (AbstractWidget button : tabButtons) {
            button.render(g, mouseX, mouseY, partialTick);
        }

        int clipRight = layout.contentX() + layout.contentW();
        g.enableScissor(layout.contentX(), layout.contentY(), clipRight, layout.contentBottom());

        // 卡片先画：它是一组字段的底，控件与文字画在它上面
        renderCards(g);
        renderFocusedRow(g);
        for (AbstractWidget widget : contentWidgets) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        List<HintText.Line> lines = new ArrayList<>(texts.size());
        for (TextEntry entry : texts) {
            Component text = entry.bold()
                    ? entry.text().copy().withStyle(style -> style.withBold(true))
                    : entry.text();
            lines.add(new HintText.Line(text, entry.x(), entry.y() - textOffset, entry.color(),
                    entry.maxWidth(), entry.maxLines()));
        }
        HintText.Line hovered = HintText.draw(g, font, lines, mouseX, mouseY);

        for (int[] separator : separators) {
            int y = separator[0] - textOffset;
            g.fill(separator[1], y, separator[1] + separator[2], y + 1, SREPanelStyle.ROW_SEPARATOR);
        }

        renderContentExtra(g, mouseX, mouseY, partialTick);
        renderPreview(g);

        g.disableScissor();

        // tooltip 不能被内容区的裁剪切掉，放在关闭裁剪之后
        if (hovered != null) {
            HintText.drawTooltip(g, font, hovered, mouseX, mouseY);
        }

        for (AbstractWidget button : footerButtons) {
            button.render(g, mouseX, mouseY, partialTick);
        }

        if (maxScroll > 0) {
            renderScrollbar(g, mouseX, mouseY);
        }
    }

    /**
     * 画卡片：一层很淡的底 + 标题条 + 描边。
     *
     * <p>
     * 卡片头本身是控件（{@link CardHeader}），负责画标题、徽标与折叠标记并处理点击，
     * 这里只负责「框」的部分，于是每块内容（一个事件 / 一个触发组 / 一个技能模块）边界清楚。
     */
    private void renderCards(GuiGraphics g) {
        for (Card card : cards) {
            int y = card.y - textOffset;
            // 卡片完全在视口外就不画（长列表里大部分卡片都是这种情况）
            if (y + card.h < layout.contentY() || y > layout.contentBottom()) {
                continue;
            }
            // 底：很淡的暗色，和面板背景区分开
            g.fill(card.x + 1, y + 1, card.x + card.w - 1, y + card.h - 1, 0x1E000000);
            // 标题条 + 它下面的分隔线（折叠时卡片只剩标题条，这条线和下边框重合，就不画了）
            g.fill(card.x + 1, y + 1, card.x + card.w - 1, y + card.headerH, SREPanelStyle.ROW_SEPARATOR);
            if (card.h > card.headerH + 1) {
                SREPanelStyle.drawFooterLine(g, card.x + 1, y + card.headerH, card.w - 2);
            }
            // 描边
            g.renderOutline(card.x, y, card.w, card.h, SREPanelStyle.CARD_BORDER);
        }
    }

    /** 焦点所在行加一层很淡的高光，一眼能看出现在在编辑哪一格。 */
    private void renderFocusedRow(GuiGraphics g) {
        if (!(getFocused() instanceof AbstractWidget focused) || !focused.visible) {
            return;
        }
        for (Row row : rows) {
            boolean contains = false;
            for (Cell cell : row.cells) {
                if (cell.widget == focused) {
                    contains = true;
                    break;
                }
            }
            if (contains) {
                int y = row.y - textOffset;
                g.fill(layout.contentX(), y - 1, layout.contentRight(), y + row.height - 2, 0x14D4AF37);
                return;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 全屏渐变底：面板不再是直接浮在游戏画面上（文档 §8 的整屏背景）
        g.fillGradient(0, 0, width, height, SREPanelStyle.SCREEN_BG_TOP, SREPanelStyle.SCREEN_BG_BOTTOM);
        SREPanelStyle.drawPanel(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH());

        // 面板内标题条：标题不再和页签抢中间那一行
        Component title = Component.translatable(translationPrefix() + ".title")
                .copy().withStyle(style -> style.withBold(true));
        g.drawString(font, title, layout.titleX(), layout.titleY(), SREPanelStyle.GOLD, false);
        SREPanelStyle.drawFooterLine(g, layout.panelX() + 1, layout.titleY() + EditorLayout.LINE_H + 2,
                layout.panelW() - 2);
    }

    private void renderPreview(GuiGraphics g) {
        if (previewSlotSize <= 0) {
            return;
        }
        // 右侧专属列是固定不动的；内容区里的一行则跟着滚动
        boolean inColumn = layout.previewW() > 0;
        int x = previewSlotX;
        int y = inColumn ? previewSlotY : previewSlotY - textOffset;
        int size = previewSlotSize;

        // 边框 + 占位棋盘：没配材质时也能看清预览的位置
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, SREPanelStyle.BORDER);
        g.fill(x, y, x + size, y + size, 0xFF120A04);
        int cell = Math.max(4, size / 7);
        for (int cy = 0; cy * cell < size; cy++) {
            for (int cx = 0; cx * cell < size; cx++) {
                if (((cx + cy) & 1) != 0) {
                    continue;
                }
                int x0 = x + cx * cell;
                int y0 = y + cy * cell;
                g.fill(x0, y0, Math.min(x + size, x0 + cell), Math.min(y + size, y0 + cell), 0xFF2A0F2A);
            }
        }
        renderPreviewContent(g, x, y, size);

        if (previewLabelKey != null && inColumn) {
            String shown = MapUiGraphics.clip(font, Component.translatable(previewLabelKey).getString(),
                    layout.previewW() + 8);
            g.drawString(font, Component.literal(shown), x + (size - font.width(shown)) / 2, y + size + 3,
                    SREPanelStyle.TEXT, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 滚动条
    // ══════════════════════════════════════════════════════════════════

    private int thumbHeight() {
        return EditorLayout.thumbHeight(layout.sbH(), maxScroll);
    }

    private int thumbY() {
        return EditorLayout.thumbY(layout.sbTop(), layout.sbH(), thumbHeight(), scroll, maxScroll);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int thumbH = thumbHeight();
        int thumbY = thumbY();
        boolean hover = isDraggingScroll
                || inside(mouseX, mouseY, layout.sbX() - 1, thumbY, EditorLayout.SCROLL_W + 2, thumbH);
        SREPanelStyle.drawScrollbar(g, layout.sbX(), layout.sbTop(), layout.sbH(), thumbY, thumbH, hover);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0 && inside(mouseX, mouseY, layout.contentX(), layout.contentY(), layout.contentW(),
                layout.contentH())) {
            scrollBy((float) (-scrollY * EditorLayout.ROW_H));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll > 0 && inside(mouseX, mouseY, layout.sbX() - 1, layout.sbTop(),
                EditorLayout.SCROLL_W + 2, layout.sbH())) {
            isDraggingScroll = true;
            dragStartY = mouseY;
            dragStartScroll = scroll;
            // 点轨道：直接把 thumb 挪到鼠标位置
            int thumbH = thumbHeight();
            int trackH = Math.max(1, layout.sbH() - thumbH);
            float ratio = (float) ((mouseY - layout.sbTop() - thumbH / 2.0D) / trackH);
            scroll = clampScroll(ratio * maxScroll);
            applyScroll();
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // 点到被裁掉一半的字段：顺手把它完整滚进视野
        if (handled && getFocused() instanceof AbstractWidget focused) {
            scrollIntoView(focused);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScroll) {
            int thumbH = thumbHeight();
            double track = Math.max(1, layout.sbH() - thumbH);
            scroll = clampScroll(dragStartScroll + (float) ((mouseY - dragStartY) / track * maxScroll));
            applyScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScroll) {
            isDraggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ══════════════════════════════════════════════════════════════════
    // 键盘：编辑更舒适
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = hasControlDown();

        // Ctrl+S / Ctrl+Enter 保存
        if (ctrl && (keyCode == GLFW.GLFW_KEY_S || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            onSave();
            return true;
        }

        // Tab / Enter：在字段之间前后跳（旧实现只能一个个点）
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (getFocused() instanceof EditBox) {
                moveFocus(hasShiftDown() ? -1 : 1);
                return true;
            }
        }

        // Esc：先取消焦点，再按一次才关闭（避免手一抖丢掉正在编辑的字段）
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && getFocused() != null) {
            setFocused(null);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollBy(layout.contentH() * 0.9F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollBy(-layout.contentH() * 0.9F);
            return true;
        }
        if (!(getFocused() instanceof EditBox)) {
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                scroll = 0;
                applyScroll();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) {
                scroll = maxScroll;
                applyScroll();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                scrollBy(EditorLayout.ROW_H);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                scrollBy(-EditorLayout.ROW_H);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 焦点在字段之间移动（负数是往前），并自动把它滚进视口。 */
    protected void moveFocus(int delta) {
        if (fieldOrder.isEmpty()) {
            return;
        }
        int current = getFocused() instanceof EditBox box ? fieldOrder.indexOf(box) : -1;
        int next = current < 0 ? 0 : Math.floorMod(current + delta, fieldOrder.size());
        EditBox box = fieldOrder.get(next);
        setFocused(box);
        box.setCursorPosition(box.getValue().length());
        scrollIntoView(box);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ══════════════════════════════════════════════════════════════════
    // 解析工具（四个界面原本各写一份）
    // ══════════════════════════════════════════════════════════════════

    protected static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    protected static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 整数显示：整数不带小数点。 */
    protected static String num(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D
                ? String.valueOf((long) Math.rint(value))
                : String.valueOf(value);
    }

    /** 逗号分隔文本 → 列表（逐项 trim，忽略空项）。 */
    protected static List<String> splitList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 用逗号分隔文本覆盖列表。 */
    protected static void replaceList(List<String> target, String value) {
        if (target == null) {
            return;
        }
        target.clear();
        target.addAll(splitList(value));
    }

    // ══════════════════════════════════════════════════════════════════
    // 内部类型
    // ══════════════════════════════════════════════════════════════════

    /** 行内一个单元：控件或纯文字。 */
    protected static final class Cell {
        private final AbstractWidget widget;
        private final Component text;
        private final int textColor;
        private int minW;
        private int maxW;
        private float weight;
        /** 「按钮自带标题」的开关单元：相邻的这种整行开关会自动并到同一行（省掉一整行的纵向空间）。 */
        private boolean mergeable;
        private int x;
        private int width;
        private int band;

        private Cell(AbstractWidget widget, Component text, int textColor, int minW, int maxW, float weight) {
            this.widget = widget;
            this.text = text;
            this.textColor = textColor;
            this.minW = Math.max(1, minW);
            this.maxW = maxW;
            this.weight = weight;
        }
    }

    private static final class Row {
        private final EditorLayout.RowKind kind;
        private final List<Cell> cells = new ArrayList<>();
        /** 所在卡片的 id（null = 不在卡片里）：卡片里的行会左右内缩，折叠时整块收起。 */
        private String cardId;
        /** 这一行是卡片头（标题条 + 折叠按钮所在行）。 */
        private boolean cardHeader;
        /** 卡片头行回指的卡片对象。 */
        private Card card;
        private String labelKey;
        private Component note;
        private int noteColor;
        private int noteMaxLines = 2;
        private boolean previewRow;
        private int y;
        private int height;

        private Row(EditorLayout.RowKind kind) {
            this.kind = kind;
        }
    }

    private record TextEntry(Component text, int x, int y, int maxWidth, int maxLines, boolean bold, int color) {
    }

    /**
     * 一张卡片：把一组相关字段（一个事件 / 一个触发组 / 一个技能模块）框起来。
     *
     * <p>
     * 构建期由 {@link #cardBegin}/{@link #cardEnd} 记录行的范围，排布时（{@link #flushRows}）再填
     * 真实坐标；渲染时先画卡片底 + 标题条 + 描边，再画里面的控件，于是每块内容边界清楚。
     * 折叠状态记在 {@link #collapsedCards} 里（id 稳定，重建界面后仍然记得）。
     */
    private static final class Card {
        private final String id;
        private final Component title;
        private final Component badge;
        /** 卡片头右侧「×」的动作（null = 不显示删除）。 */
        private final Runnable onDelete;
        /** 卡片头所在行在 {@code rows} 里的下标；{@code endRowIndex} 是卡片后第一行。 */
        private int startRowIndex;
        private int endRowIndex;
        private int x;
        private int y;
        private int w;
        private int h;
        /** 标题条相对卡片底的高度。 */
        private int headerH;

        private Card(String id, Component title, Component badge, Runnable onDelete) {
            this.id = id;
            this.title = title;
            this.badge = badge;
            this.onDelete = onDelete;
        }
    }

    /**
     * 卡片头：一条可点的标题栏。
     *
     * <p>
     * 点一下折叠/展开这一块（折叠后只剩这一行）；标题左边是名称、右边是折叠标记，中间可以带一个
     * 小字徽标（事件类型 / 条件数量这类）。文字放不下就省略号截断，悬停看全文。
     */
    private final class CardHeader extends AbstractWidget {
        /** 右侧「×」占的宽度（含一点间距）。 */
        private static final int DELETE_W = 18;

        private final Card card;
        private boolean clipTooltipShown;

        private CardHeader(Card card) {
            super(0, 0, 120, EditorLayout.ROW_H, Component.empty());
            this.card = card;
        }

        private boolean deleteVisible() {
            return card.onDelete != null;
        }

        private boolean overDelete(int mouseX) {
            return deleteVisible() && mouseX >= getX() + getWidth() - DELETE_W;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean collapsed = collapsedCards.contains(card.id);
            int textY = getY() + (getHeight() - 8) / 2;
            // 整条标题栏一起高亮（含右侧 × 那一段），一眼看清点的是哪一块
            if (isHovered()) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), SREPanelStyle.ROW_HOVER);
            }
            if (overDelete(mouseX)) {
                g.fill(getX() + getWidth() - DELETE_W, getY(), getX() + getWidth(), getY() + getHeight(),
                        SREPanelStyle.ROW_HOVER);
            }

            int reserve = DELETE_W + 14;
            Component text = card.badge == null || card.badge.getString().isEmpty()
                    ? card.title.copy().withStyle(style -> style.withColor(SREPanelStyle.GOLD))
                    : card.title.copy().withStyle(style -> style.withColor(SREPanelStyle.GOLD))
                            .append(Component.literal("  ").withStyle(style -> style.withColor(SREPanelStyle.MUTED)))
                            .append(card.badge.copy().withStyle(style -> style.withColor(SREPanelStyle.MUTED)));
            String shown = MapUiGraphics.clip(font, text.getString(), Math.max(8, getWidth() - reserve));
            applyClipTooltip(this, shown.equals(text.getString()), text.getString(), clipTooltipShown);
            g.drawString(font, Component.literal(shown), getX() + 4, textY, SREPanelStyle.GOLD, false);
            // 折叠标记紧挨着 × 左边
            int markerX = getX() + getWidth() - reserve + 2;
            g.drawString(font, Component.literal(collapsed ? "▸" : "▾"), markerX, textY, SREPanelStyle.MUTED, false);
            if (deleteVisible()) {
                int color = overDelete(mouseX) ? SREPanelStyle.RED : SREPanelStyle.MUTED;
                g.drawString(font, Component.literal("×"), getX() + getWidth() - DELETE_W + 4, textY, color, false);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (overDelete((int) mouseX)) {
                card.onDelete.run();
                requestRebuild();
                return;
            }
            if (!collapsedCards.remove(card.id)) {
                collapsedCards.add(card.id);
            }
            requestRebuild();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
