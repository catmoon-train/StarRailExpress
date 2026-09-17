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

package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import io.wifi.ConfigCompact.annotation.Category;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.client.gui.widget.SreSwitchButton;
import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.lang.reflect.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * “全部设置”标签页模块。
 * 以树形结构展示所有配置项，支持展开/折叠嵌套对象。
 * 字段标签的翻译键：
 * - 根对象字段：sre.map_helper.settings.<路径>
 * - 嵌套对象字段：sre.map_helper.settings.class.<类名>.<字段名>
 * 工具提示：对应键 + ".@tooltip"
 * 集合（Collection）：
 * - 元素为自定义类型：叶子，右侧显示 Add, JSON, Clear, View 按钮
 * - 元素为内置类型：可展开，子行显示每个元素输入框 + Remove，末尾添加 Add 行
 */
public class AllSettingsModule implements TabModule {
    private static final Gson GSON = new Gson();
    private List<SettingsEntry> allSettingsEntries = new ArrayList<>();
    private int totalContentHeight = 0;

    /**
     * 上一次建树用的根对象。
     *
     * <p>
     * {@code AreasWorldComponent} 在载入地图 / 配置同步时会<b>整体替换</b> {@code areasSettings}，
     * 所以这里靠对象身份比较来决定「要不要重建整棵树」：换了就重建（并按下标恢复展开状态），
     * 没换就只把每一项的值重新读一遍。以前是「只建一次 + 值永久冻结」，载入地图后整页都是旧快照。
     */
    private Object cachedRoot;

    // 保存根对象，用于判断字段是否属于根对象
    private Object rootSettings;

    /** 展开过的条目路径：重建树时按路径恢复，载入地图后不会把展开的分组全收起来。 */
    private final Set<String> expandedPaths = new HashSet<>();
    /** 折叠起来的分类（点分类头切换）：71 个设置项太长，按分类收起来才好找。 */
    private final Set<String> collapsedCategories = new HashSet<>();
    /** 搜索词（小写）。非空时只列命中项、忽略分类折叠。 */
    private String searchQuery = "";
    private EditBox searchBox;
    /**
     * 标签列宽度：按当前显示的条目里最宽的标签自适应，最多占内容宽度的一半。
     *
     * <p>
     * 控件一律紧跟在这一列之后（左对齐），于是既不会出现「标签和控件之间空一大段」，
     * 长标签也不会被截断成看不懂的样子。
     */
    private int labelColumnWidth = 100;
    /** 「全部设置」按分类切成卡片：构建时记下每张卡的范围，渲染时垫在控件下面（与编辑器同一个卡片观感）。 */
    private final List<CategoryCard> categoryCards = new ArrayList<>();

    /** 搜索框占页签栏下面的一条常驻带（不随内容滚动）。 */
    @Override
    public int topStripHeight() {
        return 24;
    }

    @Override
    public void buildTopStrip(LayoutContext layout, ModuleContext ctx, List<AbstractWidget> fixed) {
        int width = Math.min(260, layout.contentWidth());
        int height = 18;
        // 贴在页签按钮正下方（LayoutContext.stripTop 就是这一段的上边界），竖直居中
        int y = layout.stripTop + Math.max(0, (layout.stripHeight() - height) / 2);
        searchBox = new EditBox(layout.font, layout.leftColumnX(), y, width, height, Component.empty());
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setHint(SREPanelStyle.hint(Component.translatable("sre.map_helper.settings.search_hint")));
        searchBox.setTooltip(Tooltip.create(Component.translatable("sre.map_helper.settings.search_hint")));
        searchBox.setResponder(value -> {
            String next = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (!next.equals(searchQuery)) {
                searchQuery = next;
                // 只重建本页签控件：搜索框是固定控件，不会被重建，所以边打字边筛不会丢焦点
                ctx.requestModuleRefresh();
            }
        });
        fixed.add(searchBox);
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.all");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        AreasWorldComponent comp = SREClient.areaComponent;
        Object root = comp == null ? null : comp.areasSettings;
        if (root == null) {
            allSettingsEntries.clear();
            cachedRoot = null;
            totalContentHeight = 0;
            return;
        }

        if (root != cachedRoot) {
            // 根对象被换过（载入地图 / 配置同步）：整棵树重建，展开状态按路径恢复
            buildEntryTree(root);
            cachedRoot = root;
        } else {
            // 只是值变了：把整棵树的值重新读一遍（updateValue 以前写好了但没人调用）
            refreshValues(allSettingsEntries);
        }

        List<Object> display = buildDisplayList();
        labelColumnWidth = measureLabelColumn(layout, display);
        categoryCards.clear();
        totalContentHeight = createWidgetsForMixedEntries(layout, ctx, placements, display, 0);
    }

    /** 标签列宽度：本页最宽标签 + 6，夹在 [60, 内容宽度的一半]。 */
    private int measureLabelColumn(LayoutContext layout, List<Object> display) {
        int widest = 0;
        for (Object obj : display) {
            if (obj instanceof SettingsEntry entry) {
                widest = Math.max(widest, layout.font.width(entry.displayName) + entry.depth * 12);
            }
        }
        return Mth.clamp(widest + 6, 60, Math.max(60, layout.contentWidth() / 2));
    }

    /** 重新读取整棵树的当前值（含嵌套条目）。 */
    private void refreshValues(List<SettingsEntry> entries) {
        for (SettingsEntry entry : entries) {
            entry.updateValue();
            refreshValues(entry.children);
        }
    }

    /** 按「分类折叠 + 搜索词」整理出要显示的条目，分类头插在分类变化处。 */
    private List<Object> buildDisplayList() {
        List<Object> flatList = new ArrayList<>();
        boolean searching = !searchQuery.isEmpty();
        List<SettingsEntry> source = searching ? flattenAll(allSettingsEntries) : allSettingsEntries;
        String lastCategory = null;
        // 每个分类一共有几项：折叠后写在标题上（「▸ 视觉 · 10 项」），免得不知道藏了什么。
        // 必须**先整体数一遍**：边遍历边数的话，标题是在该分类第一项处插的，永远只会数到 1。
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (SettingsEntry entry : source) {
            categoryCounts.merge(getCategoryId(entry.field), 1, Integer::sum);
        }
        for (SettingsEntry entry : source) {
            String category = getCategoryId(entry.field);
            boolean collapsed = !searching && collapsedCategories.contains(category);
            if (searching && !matchesQuery(entry, searchQuery)) {
                continue;
            }
            if (!Objects.equals(category, lastCategory)) {
                // 分类头**必须**先进列表再判断折叠：否则折叠后连标题一起消失，
                // 就没有任何地方能点回来（「折叠一次就再也打不开」）。
                flatList.add(new CategoryHeaderEntry(getCategoryDisplayName(category), category,
                        categoryCounts.getOrDefault(category, 0)));
                lastCategory = category;
            }
            if (collapsed) {
                continue; // 折叠的分类只留标题
            }
            flatList.add(entry);
        }
        if (searching && flatList.isEmpty()) {
            flatList.add(new NoMatchEntry());
        }
        return flatList;
    }

    /** 把整棵树按「父在前、子在后」平铺（搜索时用，这样父子都能被搜到）。 */
    private List<SettingsEntry> flattenAll(List<SettingsEntry> entries) {
        List<SettingsEntry> out = new ArrayList<>();
        for (SettingsEntry entry : entries) {
            out.add(entry);
            out.addAll(flattenAll(entry.children));
        }
        return out;
    }

    private boolean matchesQuery(SettingsEntry entry, String query) {
        // 索引里已经含「原文 / 全拼 / 首字母」三份，查一次就够
        return entry.searchIndex.contains(query);
    }

    /** 搜索中：不自动展开子项，避免同一项既作为命中项出现、又作为父项的子项重复出现。 */
    private boolean searching() {
        return !searchQuery.isEmpty();
    }

    private void buildEntryTree(Object root) {
        allSettingsEntries.clear();
        rootSettings = root;
        Class<?> clazz = root.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!shouldShowField(field))
                continue;
            SettingsEntry entry = new SettingsEntry(field.getName(), field, rootSettings, 0);
            if (shouldExpandObject(entry.currentValue))
                expandObject(entry);
            allSettingsEntries.add(entry);
        }
        applyExpandedState(allSettingsEntries);
    }

    /** 按路径恢复展开状态。 */
    private void applyExpandedState(List<SettingsEntry> entries) {
        for (SettingsEntry entry : entries) {
            entry.expanded = expandedPaths.contains(entry.path);
            applyExpandedState(entry.children);
        }
    }

    /** 记住展开/收起（重建树时用）。 */
    private void recordExpanded(SettingsEntry entry) {
        if (entry.expanded) {
            expandedPaths.add(entry.path);
        } else {
            expandedPaths.remove(entry.path);
        }
    }

    /** 条目类型：没有匹配项时提示用。 */
    private record NoMatchEntry() {
    }

    /**
     * 标签被截断时挂一个「悬停看全文」的 tooltip，放得下就摘掉。
     *
     * <p>
     * 只在状态变化时动 tooltip（{@code shown[0]} 记住上一次的状态），避免每帧新建对象。
     */
    private static void applyClipTooltip(AbstractWidget widget, boolean fits, String full, boolean[] shown) {
        if (fits == !shown[0]) {
            return;
        }
        shown[0] = !fits;
        widget.setTooltip(fits ? null : Tooltip.create(Component.literal(full)));
    }

    @Override
    public int getContentHeight() {
        return totalContentHeight;
    }

    // ── Helpers ─────────────────────────────────────────────────────
    private boolean shouldShowField(Field field) {
        // 与 FormAddScreen 的口径保持一致：静态/瞬态/合成字段不是这份地图配置的一部分
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
            return false;
        }
        if (field.isAnnotationPresent(Expose.class)) {
            Expose expose = field.getAnnotation(Expose.class);
            return expose.serialize() && expose.deserialize();
        }
        return true;
    }

    private String getCategoryId(Field field) {
        try {
            if (field.isAnnotationPresent(Category.class))
                return field.getAnnotation(Category.class).value();
        } catch (Exception e) {
        }
        return "default";
    }

    private String getCategoryDisplayName(String categoryId) {
        if (categoryId == null)
            categoryId = "default";
        return Component.translatableWithFallback("sre.map_helper.settings.category." + categoryId, categoryId)
                .getString();
    }

    /**
     * 判断一个对象是否应该被展开（即作为非叶子节点）。
     * 对于 Collection，默认不展开（由外部控制）。
     */
    private boolean shouldExpandObject(Object obj) {
        if (obj == null)
            return false;
        Class<?> clazz = obj.getClass();
        if (Collection.class.isAssignableFrom(clazz)) {
            return false; // 集合的展开由 createWidgetsForEntry 控制
        }
        // 其他对象：非基本、非枚举、非字符串、非Map、非数字、非布尔值 -> 可展开
        return !clazz.isPrimitive() && !clazz.isEnum() && clazz != String.class &&
                !Map.class.isAssignableFrom(clazz) && !Number.class.isAssignableFrom(clazz) &&
                !Boolean.class.isAssignableFrom(clazz);
    }

    /**
     * 展开一个对象，生成其子字段条目。
     * 注意：Collection 不会通过这里展开。
     */
    private void expandObject(SettingsEntry parent) {
        Object obj = parent.currentValue;
        if (obj == null)
            return;
        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!shouldShowField(field))
                continue;
            SettingsEntry child = new SettingsEntry(parent.path + "." + field.getName(), field, obj, parent.depth + 1);
            if (shouldExpandObject(child.currentValue))
                expandObject(child);
            parent.children.add(child);
        }
    }

    /**
     * 判断是否为内置类型（即不是自定义 POJO）。
     * 参考 NbtSerializer 中的类型支持列表。
     */
    private boolean isBuiltinType(Class<?> clazz) {
        if (clazz == null)
            return true;
        // 基本类型及包装类
        if (clazz.isPrimitive())
            return true;
        if (clazz == Boolean.class || clazz == Byte.class || clazz == Short.class ||
                clazz == Integer.class || clazz == Long.class || clazz == Float.class ||
                clazz == Double.class || clazz == Character.class)
            return true;
        // 字符串
        if (clazz == String.class)
            return true;
        // 枚举
        if (clazz.isEnum())
            return true;
        // 原子类
        if (clazz == AtomicInteger.class || clazz == AtomicLong.class || clazz == AtomicBoolean.class)
            return true;
        // Optional
        if (clazz == Optional.class || clazz == OptionalInt.class ||
                clazz == OptionalLong.class || clazz == OptionalDouble.class)
            return true;
        // 日期时间
        if (clazz == UUID.class || clazz == Date.class || clazz == Instant.class ||
                clazz == LocalDate.class || clazz == LocalDateTime.class)
            return true;
        // 数组
        if (clazz.isArray())
            return true;
        // 集合和Map本身视为内置容器
        if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz))
            return true;
        // 数值类型
        if (Number.class.isAssignableFrom(clazz))
            return true;
        return false;
    }

    /**
     * 从字段中提取集合的元素类型（泛型参数）。
     * 使用更稳健的方式：如果遇到 Wildcard 或 TypeVariable，返回 Object.class。
     */
    private Class<?> getElementType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0) {
                Type arg = args[0];
                if (arg instanceof Class) {
                    return (Class<?>) arg;
                } else if (arg instanceof ParameterizedType) {
                    return (Class<?>) ((ParameterizedType) arg).getRawType();
                } else if (arg instanceof WildcardType) {
                    // 取上限
                    WildcardType wildcard = (WildcardType) arg;
                    Type[] upper = wildcard.getUpperBounds();
                    if (upper.length > 0 && upper[0] instanceof Class) {
                        return (Class<?>) upper[0];
                    }
                    return Object.class;
                } else {
                    // TypeVariable 或其他，返回 Object
                    return Object.class;
                }
            }
        }
        return null;
    }

    // ── Widget creation ─────────────────────────────────────────────
    private int createWidgetsForMixedEntries(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements,
            List<Object> list, int yOffset) {
        int currentY = yOffset;
        for (Object obj : list) {
            if (obj instanceof NoMatchEntry) {
                currentY += createNoMatchHint(layout, placements, currentY);
            } else if (obj instanceof CategoryHeaderEntry header) {
                closeLastCategoryCard(currentY);
                int headerY = currentY;
                currentY += createWidgetsForCategoryHeader(layout, ctx, placements, header, currentY);
                categoryCards.add(new CategoryCard(header.categoryId, categoryCardLeft(layout), headerY,
                        categoryCardWidth(layout), collapsedCategories.contains(header.categoryId)));
            } else if (obj instanceof SettingsEntry entry) {
                currentY += createWidgetsForEntry(layout, ctx, placements, entry, currentY);
                // 如果是展开的集合且元素为内置类型，则动态生成子行（不依赖 children）
                if (searching()) {
                    // 搜索时命中项已平铺列出，这里不再展开，免得同一项出现两次
                } else if (entry.expanded && isCollectionWithBuiltinElements(entry)) {
                    currentY = createWidgetsForCollectionChildren(layout, ctx, placements, entry, currentY);
                } else if (entry.expanded && !entry.children.isEmpty()) {
                    // 普通对象的子节点
                    currentY = createWidgetsForEntries(layout, ctx, placements, entry.children, currentY);
                }
            }
            closeLastCategoryCard(currentY);
        }
        closeLastCategoryCard(currentY);
        return currentY;
    }

    /** 把最后一张分类卡片的高度收到当前位置（每行结束都调一次，分类就自动包住自己的全部内容）。 */
    private void closeLastCategoryCard(int currentY) {
        if (categoryCards.isEmpty()) {
            return;
        }
        CategoryCard card = categoryCards.get(categoryCards.size() - 1);
        card.h = Math.max(1, currentY - card.y);
    }

    /** 卡片左右各比内容宽 4px：给描边留出空间，里面的行位置完全不用动。 */
    private static int categoryCardLeft(LayoutContext layout) {
        return Math.max(layout.panelLeftX, layout.leftColumnX() - 4);
    }

    private static int categoryCardWidth(LayoutContext layout) {
        return Math.max(40, layout.rightEdge() - categoryCardLeft(layout) + 4);
    }

    /**
     * 直接不画了
     */
    @Override
    public void renderContentBackground(GuiGraphics g, int scrollOffset) {
    }

    private int createWidgetsForEntries(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements,
            List<SettingsEntry> entries, int yOffset) {
        int currentY = yOffset;
        for (SettingsEntry entry : entries) {
            currentY += createWidgetsForEntry(layout, ctx, placements, entry, currentY);
            if (searching()) {
                // 同上：搜索时不展开
            } else if (entry.expanded && isCollectionWithBuiltinElements(entry)) {
                currentY = createWidgetsForCollectionChildren(layout, ctx, placements, entry, currentY);
            } else if (entry.expanded && !entry.children.isEmpty()) {
                currentY = createWidgetsForEntries(layout, ctx, placements, entry.children, currentY);
            }
        }
        return currentY;
    }

    private int createWidgetsForCategoryHeader(LayoutContext layout, ModuleContext ctx,
            List<WidgetPlacement> placements, CategoryHeaderEntry header, int y) {
        int leftX = layout.leftColumnX();
        int width = layout.contentWidth();
        int height = 24;
        boolean collapsed = collapsedCategories.contains(header.categoryId);
        // 分类头是可点的：点一下折叠/展开这个分类（71 个设置项全靠它分组）
        String caption = (collapsed ? "▸ " : "▾ ") + header.displayName
                + (collapsed ? "  ·  " + Component
                        .translatable("sre.map_helper.settings.category_count", header.count).getString() : "");
        CategoryLabel label = new CategoryLabel(layout.font, leftX, y, width, height, caption,
                () -> {
                    if (!collapsedCategories.remove(header.categoryId)) {
                        collapsedCategories.add(header.categoryId);
                    }
                    // 折叠状态改了，要重建一次列表才看得出来
                    ctx.requestModuleRefresh();
                });
        placements.add(new WidgetPlacement(label, y));
        return height;
    }

    /** 搜索没有命中时给一行提示（而不是一片空白）。 */
    private int createNoMatchHint(LayoutContext layout, List<WidgetPlacement> placements, int y) {
        FieldLabel label = new FieldLabel(layout.font, layout.leftColumnX(), y, layout.contentWidth(), 20,
                Component.translatable("sre.map_helper.settings.no_match").getString());
        placements.add(new WidgetPlacement(label, y));
        return 24;
    }

    /**
     * 判断条目是否为 Collection 且元素类型为内置类型（即可展开）。
     */
    private boolean isCollectionWithBuiltinElements(SettingsEntry entry) {
        Class<?> type = entry.field.getType();
        if (!Collection.class.isAssignableFrom(type))
            return false;
        Class<?> elementType = getElementType(entry.field);
        if (elementType == null)
            return true; // 未知类型，视为内置（简单列表）
        return isBuiltinType(elementType);
    }

    /**
     * 判断条目是否为 Collection 且元素类型为自定义类型（叶子）。
     */
    private boolean isCollectionWithCustomElements(SettingsEntry entry) {
        Class<?> type = entry.field.getType();
        if (!Collection.class.isAssignableFrom(type))
            return false;
        Class<?> elementType = getElementType(entry.field);
        if (elementType == null)
            return false;
        return !isBuiltinType(elementType);
    }

    /**
     * 动态生成集合（内置类型元素）的子行。
     * 每行包含一个输入框（显示当前元素值）和一个移除按钮，最后一行是添加新元素的输入框和按钮。
     */
    private int createWidgetsForCollectionChildren(LayoutContext layout, ModuleContext ctx,
            List<WidgetPlacement> placements, SettingsEntry entry, int y) {
        int currentY = y;
        int depth = entry.depth + 1;
        int leftX = layout.leftColumnX() + depth * 12;
        int rightEdge = layout.rightEdge();
        int gap = 6;
        int rowHeight = 30;
        String tooltipKey = "sre.map_helper.settings." + entry.path + ".@tooltip";

        Class<?> elementType = getElementType(entry.field);
        boolean isEnum = elementType != null && elementType.isEnum();

        // ---- 添加行 ----
        String addLabelKey = "sre.map_helper.settings." + entry.path + ".add";
        String addLabelText = I18n.exists(addLabelKey) ? Component.translatable(addLabelKey).getString()
                : Component.translatableWithFallback("sre.map_helper.add.inner", "(Add)").getString();
        int addLabelW = layout.font.width(addLabelText) + 4;
        FieldLabel addLabel = new FieldLabel(layout.font, leftX, currentY, addLabelW, 20, addLabelText);
        placements.add(new WidgetPlacement(addLabel, currentY));

        int controlStartX = leftX + addLabelW + gap;
        int remainingWidth = rightEdge - controlStartX;

        if (isEnum) {
            Object[] constants = elementType.getEnumConstants();
            final int[] addSelectedIndex = { 0 };
            String initialEnumName = ((Enum<?>) constants[0]).name();
            String displayKey = "sre.map_helper.settings." + entry.path + "." + initialEnumName;
            String displayText = Component.translatableWithFallback(displayKey, initialEnumName).getString();

            int arrowBtnW = 20, displayW = 80, gapBtn = 4;
            int totalW = arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + 40 + gap;
            // 标签很长时 startX 可能被推到 controlStartX 左边：夹一下，保证不压到标签上
            int startX = controlStartX;

            EnumValueLabel enumDisplay = new EnumValueLabel(layout.font, startX + arrowBtnW + gapBtn, currentY,
                    displayW, 20, displayText);
            // 初始tooltip
            String initialTooltipKey = "sre.map_helper.settings." + entry.path + "." + initialEnumName + ".@tooltip";
            if (I18n.exists(initialTooltipKey)) {
                enumDisplay.setTooltip(Tooltip.create(Component.translatable(initialTooltipKey)));
            }
            placements.add(new WidgetPlacement(enumDisplay, currentY));

            SreButton leftArrow = SreButton.create(Component.literal("<"), b -> {
                int idx = addSelectedIndex[0];
                int newIdx = (idx - 1 + constants.length) % constants.length;
                String newName = ((Enum<?>) constants[newIdx]).name();
                addSelectedIndex[0] = newIdx;
                String newDisplayKey = "sre.map_helper.settings." + entry.path + "." + newName;
                enumDisplay.setText(Component.translatableWithFallback(newDisplayKey, newName).getString());
                String newTooltipKey = "sre.map_helper.settings." + entry.path + "." + newName + ".@tooltip";
                if (I18n.exists(newTooltipKey)) {
                    enumDisplay.setTooltip(Tooltip.create(Component.translatable(newTooltipKey)));
                } else {
                    enumDisplay.setTooltip(null);
                }
            }).bounds(startX, currentY, arrowBtnW, 20).build();
            placements.add(new WidgetPlacement(leftArrow, currentY));

            SreButton rightArrow = SreButton.create(Component.literal(">"), b -> {
                int idx = addSelectedIndex[0];
                int newIdx = (idx + 1) % constants.length;
                String newName = ((Enum<?>) constants[newIdx]).name();
                addSelectedIndex[0] = newIdx;
                String newDisplayKey = "sre.map_helper.settings." + entry.path + "." + newName;
                enumDisplay.setText(Component.translatableWithFallback(newDisplayKey, newName).getString());
                String newTooltipKey = "sre.map_helper.settings." + entry.path + "." + newName + ".@tooltip";
                if (I18n.exists(newTooltipKey)) {
                    enumDisplay.setTooltip(Tooltip.create(Component.translatable(newTooltipKey)));
                } else {
                    enumDisplay.setTooltip(null);
                }
            }).bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn, currentY, arrowBtnW, 20)
.build();
            placements.add(new WidgetPlacement(rightArrow, currentY));

            SreButton addBtn = SreButton.create(
                    Component.translatable("sre.map_helper.add"),
                    b -> {
                        String name = ((Enum<?>) constants[addSelectedIndex[0]]).name();
                        ctx.sendOnly("sre:area_manager add " + entry.path + " " + name);
                    })
                    .bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + gapBtn, currentY, 40, 20)
.build();
            placements.add(new WidgetPlacement(addBtn, currentY));
        } else {
            int inputWidth = Mth.clamp(remainingWidth - 40 - gap, 40, 100);
            EditBox addInput = new EditBox(layout.font, controlStartX, currentY, inputWidth, 20,
                    Component.translatable("sre.map_helper.value"));
            if (I18n.exists(tooltipKey)) {
                addInput.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            }
            placements.add(new WidgetPlacement(addInput, currentY));

            int addBtnX = controlStartX + inputWidth + gap;
            SreButton addBtn = SreButton.create(
                    Component.translatable("sre.map_helper.add"),
                    b -> {
                        String val = addInput.getValue().trim();
                        if (!val.isEmpty())
                            ctx.sendOnly("sre:area_manager add " + entry.path + " " + (val));
                    })
                    .bounds(addBtnX, currentY, 40, 20)
.build();
            placements.add(new WidgetPlacement(addBtn, currentY));
        }

        currentY += rowHeight;

        // ---- 删除行 ----
        String deleteLabelKey = "sre.map_helper.settings." + entry.path + ".delete";
        String deleteLabelText = I18n.exists(deleteLabelKey) ? Component.translatable(deleteLabelKey).getString()
                : Component.translatableWithFallback("sre.map_helper.delete.inner", "(Remove)").getString();
        int deleteLabelW = layout.font.width(deleteLabelText) + 4;
        FieldLabel deleteLabel = new FieldLabel(layout.font, leftX, currentY, deleteLabelW, 20, deleteLabelText);
        placements.add(new WidgetPlacement(deleteLabel, currentY));

        int deleteControlStartX = leftX + deleteLabelW + gap;
        int deleteRemainingWidth = rightEdge - deleteControlStartX;

        if (isEnum) {
            Object[] constants = elementType.getEnumConstants();
            final int[] deleteSelectedIndex = { 0 };
            String initialEnumName = ((Enum<?>) constants[0]).name();
            String displayKey = "sre.map_helper.settings." + entry.path + "." + initialEnumName;
            String displayText = Component.translatableWithFallback(displayKey, initialEnumName).getString();

            int arrowBtnW = 20, displayW = 80, gapBtn = 4;
            int totalW = arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + 40 + gap;
            int startX = deleteControlStartX;

            EnumValueLabel enumDisplay = new EnumValueLabel(layout.font, startX + arrowBtnW + gapBtn, currentY,
                    displayW, 20, displayText);
            String initialTooltipKey = "sre.map_helper.settings." + entry.path + "." + initialEnumName + ".@tooltip";
            if (I18n.exists(initialTooltipKey)) {
                enumDisplay.setTooltip(Tooltip.create(Component.translatable(initialTooltipKey)));
            }
            placements.add(new WidgetPlacement(enumDisplay, currentY));

            SreButton leftArrow = SreButton.create(Component.literal("<"), b -> {
                int idx = deleteSelectedIndex[0];
                int newIdx = (idx - 1 + constants.length) % constants.length;
                String newName = ((Enum<?>) constants[newIdx]).name();
                deleteSelectedIndex[0] = newIdx;
                String newDisplayKey = "sre.map_helper.settings." + entry.path + "." + newName;
                enumDisplay.setText(Component.translatableWithFallback(newDisplayKey, newName).getString());
                String newTooltipKey = "sre.map_helper.settings." + entry.path + "." + newName + ".@tooltip";
                if (I18n.exists(newTooltipKey)) {
                    enumDisplay.setTooltip(Tooltip.create(Component.translatable(newTooltipKey)));
                } else {
                    enumDisplay.setTooltip(null);
                }
            }).bounds(startX, currentY, arrowBtnW, 20).build();
            placements.add(new WidgetPlacement(leftArrow, currentY));

            SreButton rightArrow = SreButton.create(Component.literal(">"), b -> {
                int idx = deleteSelectedIndex[0];
                int newIdx = (idx + 1) % constants.length;
                String newName = ((Enum<?>) constants[newIdx]).name();
                deleteSelectedIndex[0] = newIdx;
                String newDisplayKey = "sre.map_helper.settings." + entry.path + "." + newName;
                enumDisplay.setText(Component.translatableWithFallback(newDisplayKey, newName).getString());
                String newTooltipKey = "sre.map_helper.settings." + entry.path + "." + newName + ".@tooltip";
                if (I18n.exists(newTooltipKey)) {
                    enumDisplay.setTooltip(Tooltip.create(Component.translatable(newTooltipKey)));
                } else {
                    enumDisplay.setTooltip(null);
                }
            }).bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn, currentY, arrowBtnW, 20)
.build();
            placements.add(new WidgetPlacement(rightArrow, currentY));

            SreButton deleteBtn = SreButton.create(
                    Component.translatable("sre.map_helper.remove"),
                    b -> {
                        String name = ((Enum<?>) constants[deleteSelectedIndex[0]]).name();
                        ctx.sendOnly("sre:area_manager remove " + entry.path + " " + name);
                    })
                    .bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + gapBtn, currentY, 40, 20)
.build();
            placements.add(new WidgetPlacement(deleteBtn, currentY));
        } else {
            int inputWidth = Mth.clamp(deleteRemainingWidth - 40 - gap, 40, 100);
            EditBox deleteInput = new EditBox(layout.font, deleteControlStartX, currentY, inputWidth, 20,
                    Component.translatable("sre.map_helper.value"));
            if (I18n.exists(tooltipKey)) {
                deleteInput.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            }
            placements.add(new WidgetPlacement(deleteInput, currentY));

            int deleteBtnX = deleteControlStartX + inputWidth + gap;
            SreButton deleteBtn = SreButton.create(
                    Component.translatable("sre.map_helper.remove"),
                    b -> {
                        String val = deleteInput.getValue().trim();
                        if (!val.isEmpty())
                            ctx.sendOnly("sre:area_manager remove " + entry.path + " " + (val));
                    })
                    .bounds(deleteBtnX, currentY, 40, 20)
.build();
            placements.add(new WidgetPlacement(deleteBtn, currentY));
        }

        currentY += rowHeight;
        return currentY;
    }

    // ── 枚举值显示标签（已在原代码中） ──
    private static class EnumValueLabel extends AbstractWidget {
        private String text;

        public EnumValueLabel(Font font, int x, int y, int width, int height, String initialText) {
            super(x, y, width, height, Component.literal(initialText));
            this.text = initialText;
        }

        public void setText(String newText) {
            this.text = newText;
            setMessage(Component.literal(newText));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int textWidth = font.width(text);
            int textX = getX() + (getWidth() - textWidth) / 2;
            int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
            g.drawString(font, text, textX, textY, 0xFFCCDDEE, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    private int createWidgetsForEntry(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements,
            SettingsEntry entry, int y) {
        int rightEdge = layout.rightEdge();
        int leftX = layout.leftColumnX() + entry.depth * 12;
        int labelWidth = Math.max(40, labelColumnWidth - entry.depth * 12);
        int gap = 6;
        Class<?> type = entry.field.getType();
        Object value = entry.currentValue;
        int usedHeight = 30;

        // ---- 字段标签 ----
        FieldLabel label = new FieldLabel(layout.font, leftX, y, labelWidth, 20, entry.displayName);
        placements.add(new WidgetPlacement(label, y));
        // 工具提示
        String tooltipKey = entry.displayNameKey + ".@tooltip";
        if (I18n.exists(tooltipKey)) {
            label.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }

        // 控件列：所有行（含嵌套行）对齐到同一列，跟在标签列之后
        int controlX = layout.leftColumnX() + labelColumnWidth + gap;
        int remainingWidth = layout.contentWidth() - (controlX - layout.leftColumnX()) - 6;

        // 处理集合类型（优先判断）
        if (Collection.class.isAssignableFrom(type)) {
            // 区分自定义元素和内置元素
            if (isCollectionWithCustomElements(entry)) {
                // 自定义元素列表：Add, JSON, Clear, View
                int btnW = 30;
                int gapBtn = 3;
                int startX = controlX;

                // Add 按钮：打开表单添加
                SreButton addBtn = SreButton.create(
                        Component.translatableWithFallback("sre.map_helper.add.form", "Add"),
                        b -> {
                            Class<?> elemType = getElementType(entry.field);
                            if (elemType != null) {
                                Minecraft.getInstance().setScreen(
                                        new FormAddScreen(entry.path, ctx, elemType,
                                                () -> ctx.requestModuleRefresh(), ctx.screen()));
                            }
                        })
                        .bounds(startX, y, btnW, 20).build();
                placements.add(new WidgetPlacement(addBtn, y));

                // JSON 按钮：打开 JSON 输入对话框
                SreButton jsonBtn = SreButton.create(
                        Component.literal("JSON"),
                        b -> Minecraft.getInstance().setScreen(
                                new JsonInputScreen(entry.path, ctx,
                                        () -> ctx.requestModuleRefresh(), ctx.screen())))
                        .bounds(startX + btnW + gapBtn, y, btnW, 20).build();
                placements.add(new WidgetPlacement(jsonBtn, y));

                // Clear 按钮
                SreButton clearBtn = SreButton.create(
                        Component.translatable("sre.map_helper.clear"),
                        b -> ctx.sendOnly("sre:area_manager clear " + entry.path))
                        .bounds(startX + (btnW + gapBtn) * 2, y, btnW, 20).build();
                placements.add(new WidgetPlacement(clearBtn, y));

                // View 按钮
                SreButton viewBtn = SreButton.create(
                        Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(startX + (btnW + gapBtn) * 3, y, btnW, 20)
                        .build();
                placements.add(new WidgetPlacement(viewBtn, y));

            } else {
                // 内置元素列表：可展开，右侧显示 展开/收起, Clear, View
                int btnW = 50;
                int viewW = 30;
                int gapBtn = 4;
                int startX = controlX;

                // 展开/收起按钮
                SreButton toggleBtn = SreButton.create(
                        Component.translatable(entry.expanded ? "sre.map_helper.expandable.unexpand"
                                : "sre.map_helper.expandable.expand"),
                        b -> {
                            entry.expanded = !entry.expanded;
                            recordExpanded(entry);
                            ctx.requestModuleRefresh();
                        })
                        .bounds(startX, y, btnW, 20).build();
                placements.add(new WidgetPlacement(toggleBtn, y));
                if (I18n.exists(tooltipKey)) {
                    toggleBtn.setTooltipText(Component.translatable(tooltipKey));
                }

                // Clear 按钮
                SreButton clearBtn = SreButton.create(
                        Component.translatable("sre.map_helper.clear"),
                        b -> ctx.sendOnly("sre:area_manager clear " + entry.path))
                        .bounds(startX + btnW + gapBtn, y, 40, 20).build();
                placements.add(new WidgetPlacement(clearBtn, y));

                // View 按钮
                SreButton viewBtn = SreButton.create(
                        Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(startX + btnW + gapBtn + 40 + gapBtn, y, viewW, 20)
                        .build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            return usedHeight;
        }

        // ----- 其他类型（非集合）的处理 -----
        if (entry.isLeaf()) {
            // 布尔类型：与四个自定义工具编辑器同款 —— 一个带色开关（✓ 开 / ✗ 关），点一下切换
            if (type == boolean.class || type == Boolean.class) {
                int swW = 64, viewW = 30, gapBtn = 4;
                int startX = controlX;
                boolean current = value instanceof Boolean bool && bool;

                SreSwitchButton toggleBtn = SreSwitchButton
                        .toggle(layout.font, current,
                                on -> Component.translatable(on.isOn() ? "sre.map_helper.value.on"
                                        : "sre.map_helper.value.off"),
                                now -> ctx.sendOnly("sre:area_manager set " + entry.path + " " + now))
                        .at(startX, y, swW, 20);
                placements.add(new WidgetPlacement(toggleBtn, y));

                SreButton viewBtn = SreButton.create(
                        Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(startX + swW + gapBtn, y, viewW, 20).build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            // 字符串 & 数字
            else if (type == String.class || isNumberType(type)) {
                int inputWidth = Mth.clamp(remainingWidth - 82, 70, 220);
                EditBox input = new EditBox(layout.font, controlX, y, inputWidth, 20, Component.empty());
                input.setMaxLength(200);
                input.setValue(value != null ? value.toString() : "");
                if (I18n.exists(tooltipKey)) {
                    input.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                }
                placements.add(new WidgetPlacement(input, y));
                SreButton modifyBtn = SreButton.create(Component.translatable("sre.map_helper.modify"), b -> {
                    String val = input.getValue().trim();
                    if (!val.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + (val));
                }).bounds(controlX + inputWidth + gap, y, 40, 20).build();
                placements.add(new WidgetPlacement(modifyBtn, y));
                SreButton viewBtn = SreButton.create(Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(controlX + inputWidth + gap + 40 + gap, y, 30, 20)
.build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            // 枚举
            else if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                if (constants == null || constants.length == 0) {
                    SreButton viewBtn = SreButton.create(Component.translatable("sre.map_helper.view"),
                            b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                            .bounds(controlX, y, 30, 20).build();
                    placements.add(new WidgetPlacement(viewBtn, y));
                } else {
                    int currentIdx = 0;
                    String currentEnumName = (value instanceof Enum<?> e) ? e.name() : "";
                    for (int i = 0; i < constants.length; i++) {
                        if (((Enum<?>) constants[i]).name().equals(currentEnumName)) {
                            currentIdx = i;
                            break;
                        }
                    }

                    int arrowBtnW = 22, displayW = 100, gapBtn = 4;
                    int startX = controlX;

                    final int[] selectedIndex = { currentIdx };

                    java.util.function.Function<Integer, String> getDisplayName = idx -> {
                        String name = ((Enum<?>) constants[idx]).name();
                        String key = "sre.map_helper.settings." + entry.path + "." + name;
                        return Component.translatableWithFallback(key, name).getString();
                    };

                    EnumValueLabel displayLabel = new EnumValueLabel(layout.font, startX + arrowBtnW + gapBtn, y,
                            displayW, 20, getDisplayName.apply(selectedIndex[0]));
                    String enumTooltipKey = "sre.map_helper.settings." + entry.path + "."
                            + ((Enum<?>) constants[selectedIndex[0]]).name() + ".@tooltip";
                    if (I18n.exists(enumTooltipKey)) {
                        displayLabel.setTooltip(Tooltip.create(Component.translatable(enumTooltipKey)));
                    }

                    SreButton leftArrow = SreButton.create(Component.literal("<"), b -> {
                        int idx = selectedIndex[0];
                        int newIdx = (idx - 1 + constants.length) % constants.length;
                        String newName = ((Enum<?>) constants[newIdx]).name();
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + (newName));
                        selectedIndex[0] = newIdx;
                        displayLabel.setText(getDisplayName.apply(newIdx));
                        String newTooltipKey = "sre.map_helper.settings." + entry.path + "." + newName + ".@tooltip";
                        if (I18n.exists(newTooltipKey)) {
                            displayLabel.setTooltip(Tooltip.create(Component.translatable(newTooltipKey)));
                        } else {
                            displayLabel.setTooltip(null);
                        }
                    }).bounds(startX, y, arrowBtnW, 20).build();

                    SreButton rightArrow = SreButton.create(Component.literal(">"), b -> {
                        int idx = selectedIndex[0];
                        int newIdx = (idx + 1) % constants.length;
                        String newName = ((Enum<?>) constants[newIdx]).name();
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + newName);
                        selectedIndex[0] = newIdx;
                        displayLabel.setText(getDisplayName.apply(newIdx));
                        String newTooltipKey = "sre.map_helper.settings." + entry.path + "." + newName + ".@tooltip";
                        if (I18n.exists(newTooltipKey)) {
                            displayLabel.setTooltip(Tooltip.create(Component.translatable(newTooltipKey)));
                        } else {
                            displayLabel.setTooltip(null);
                        }
                    }).bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn, y, arrowBtnW, 20)
.build();

                    SreButton viewBtn = SreButton.create(Component.translatable("sre.map_helper.view"),
                            b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                            .bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + gap, y, 30, 20)
.build();
                    placements.add(new WidgetPlacement(viewBtn, y));
                    placements.add(new WidgetPlacement(leftArrow, y));
                    placements.add(new WidgetPlacement(displayLabel, y));
                    placements.add(new WidgetPlacement(rightArrow, y));
                }
            }
            // Map 类型
            else if (Map.class.isAssignableFrom(type)) {
                int inputWidth = Mth.clamp(remainingWidth - 82, 70, 220);
                EditBox mapInput = new EditBox(layout.font, controlX, y, inputWidth, 20,
                        Component.translatable("sre.map_helper.json"));
                mapInput.setMaxLength(1000);
                mapInput.setValue(value != null ? GSON.toJson(value) : "{}");
                if (I18n.exists(tooltipKey)) {
                    mapInput.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                }
                placements.add(new WidgetPlacement(mapInput, y));
                SreButton modifyBtn = SreButton.create(Component.translatable("sre.map_helper.modify"), b -> {
                    String json = mapInput.getValue().trim();
                    if (!json.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + (json));
                }).bounds(controlX + inputWidth + gap, y, 40, 20).build();
                placements.add(new WidgetPlacement(modifyBtn, y));
                SreButton viewBtn = SreButton.create(Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(controlX + inputWidth + gap + 40 + gap, y, 30, 20)
                        .build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            // 其他类型（自定义对象）但已经是叶子？理论上自定义对象应展开，所以不会到这里
        } else {
            // 非叶子节点（普通自定义对象），仅显示展开/收起按钮
            SreButton toggleBtn = SreButton.create(
                    Component.translatable(!entry.expanded ? "sre.map_helper.expandable.expand"
                            : "sre.map_helper.expandable.unexpand"),
                    b -> {
                        entry.expanded = !entry.expanded;
                        ctx.requestModuleRefresh();
                    }).bounds(controlX, y, 50, 20).build();
            if (I18n.exists(tooltipKey)) {
                toggleBtn.setTooltipText(Component.translatable(tooltipKey));
            }
            placements.add(new WidgetPlacement(toggleBtn, y));
        }
        return usedHeight;
    }

    private boolean isNumberType(Class<?> type) {
        if (Number.class.isAssignableFrom(type))
            return true;
        if (type == Integer.class || type == int.class ||
                type == Long.class || type == long.class ||
                type == Double.class || type == double.class ||
                type == Float.class || type == float.class)
            return true;
        return false;
    }

    // ── JSON 输入对话框（原有，保留） ──────────────────────
    private static class JsonInputScreen extends Screen {
        private final String path;
        private final ModuleContext ctx;
        private final Runnable onSuccess;
        private EditBox jsonInput;
        private SreButton confirmBtn;
        private SreButton cancelBtn;
        private final Screen parent;

        protected JsonInputScreen(String path, ModuleContext ctx, Runnable onSuccess, Screen parent) {
            super(Component.translatable("sre.map_helper.add_json.title"));
            this.path = path;
            this.ctx = ctx;
            this.onSuccess = onSuccess;
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            int centerX = width / 2;
            int centerY = height / 2;

            jsonInput = new EditBox(font, centerX - 100, centerY - 20, 200, 20,
                    Component.translatable("sre.map_helper.add_json.placeholder"));
            jsonInput.setMaxLength(10000);
            jsonInput.setValue("{}");
            addRenderableWidget(jsonInput);

            confirmBtn = SreButton.create(
                    Component.translatable("sre.map_helper.confirm"),
                    b -> {
                        String json = jsonInput.getValue().trim();
                        if (!json.isEmpty()) {
                            ctx.sendOnly("sre:area_manager add " + path + " " + (json));
                            if (onSuccess != null)
                                onSuccess.run();
                        }
                        onClose();
                    })
                    .bounds(centerX - 105, centerY + 10, 100, 20)
.build();
            addRenderableWidget(confirmBtn);

            cancelBtn = SreButton.create(
                    Component.translatable("sre.map_helper.cancel"),
                    b -> onClose())
                    .bounds(centerX + 5, centerY + 10, 100, 20)
.build();
            addRenderableWidget(cancelBtn);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(parent);
        }
    }

    // ── 新增：表单式添加复杂元素的 Screen ──────────────────────
    private static class FormAddScreen extends Screen {
        private final String path;
        private final ModuleContext ctx;
        private final Class<?> elementType;
        private final Runnable onSuccess;
        private final Screen parent;
        private final List<FieldRow> fieldRows = new ArrayList<>();

        private static class FieldRow {
            final Field field;
            final String label;
            AbstractWidget widget;
            java.util.function.Supplier<JsonElement> valueSupplier;

            FieldRow(Field field, String label) {
                this.field = field;
                this.label = label;
            }
        }

        public FormAddScreen(String path, ModuleContext ctx, Class<?> elementType, Runnable onSuccess, Screen parent) {
            super(Component.translatableWithFallback("sre.map_helper.form.title",
                    "Add " + elementType.getSimpleName()));
            this.path = path;
            this.ctx = ctx;
            this.elementType = elementType;
            this.onSuccess = onSuccess;
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            fieldRows.clear();
            int y = 35;
            int labelWidth = Math.min(200, Math.max(100, width / 4));
            int fieldStartX = 10 + labelWidth + 5;
            int fieldWidth = width - fieldStartX - 10;
            Font font = this.font;

            Field[] fields = elementType.getDeclaredFields();
            for (Field f : fields) {
                if (!shouldShowFieldStatic(f))
                    continue;
                f.setAccessible(true);
                String fieldName = Component.translatableWithFallback(
                        "sre.map_helper.settings.class." + elementType.getSimpleName() + "." + f.getName(),
                        f.getName()).getString();
                FieldRow row = new FieldRow(f, fieldName);
                Class<?> type = f.getType();

                if (type == boolean.class || type == Boolean.class) {
                    // 改为输入框，解析 true/false
                    EditBox edit = new EditBox(font, fieldStartX, y, fieldWidth, 20, Component.empty());
                    edit.setMaxLength(5);
                    edit.setValue("false");
                    row.widget = edit;
                    row.valueSupplier = () -> {
                        String val = edit.getValue().trim();
                        return new JsonPrimitive("true".equalsIgnoreCase(val));
                    };
                } else if (type.isEnum()) {
                    Object[] constants = type.getEnumConstants();
                    if (constants != null && constants.length > 0) {
                        final int[] idx = { 0 };
                        SreButton enumBtn = SreButton.create(
                                Component.literal(((Enum<?>) constants[0]).name()), b -> {
                                    idx[0] = (idx[0] + 1) % constants.length;
                                    b.setMessage(Component.literal(((Enum<?>) constants[idx[0]]).name()));
                                }).bounds(fieldStartX, y, Math.min(120, fieldWidth), 20).build();
                        row.widget = enumBtn;
                        row.valueSupplier = () -> new JsonPrimitive(((Enum<?>) constants[idx[0]]).name());
                    } else {
                        // 空枚举，回退为文本输入
                        EditBox edit = new EditBox(font, fieldStartX, y, fieldWidth, 20, Component.empty());
                        edit.setValue("");
                        row.widget = edit;
                        row.valueSupplier = () -> new JsonPrimitive(edit.getValue());
                    }
                } else if (type == String.class || Number.class.isAssignableFrom(type) || type.isPrimitive()) {
                    EditBox edit = new EditBox(font, fieldStartX, y, fieldWidth, 20, Component.empty());
                    edit.setMaxLength(200);
                    edit.setValue("");
                    row.widget = edit;
                    row.valueSupplier = () -> {
                        String val = edit.getValue().trim();
                        if (type == String.class)
                            return new JsonPrimitive(val);
                        try {
                            if (type == int.class || type == Integer.class)
                                return new JsonPrimitive(Integer.parseInt(val));
                            if (type == long.class || type == Long.class)
                                return new JsonPrimitive(Long.parseLong(val));
                            if (type == double.class || type == Double.class)
                                return new JsonPrimitive(Double.parseDouble(val));
                            if (type == float.class || type == Float.class)
                                return new JsonPrimitive(Float.parseFloat(val));
                            if (Number.class.isAssignableFrom(type))
                                return new JsonPrimitive(new com.google.gson.internal.LazilyParsedNumber(val));
                        } catch (NumberFormatException ignored) {
                        }
                        return new JsonPrimitive(val);
                    };
                } else {
                    // 复杂类型、集合、Map 等使用 JSON 文本输入
                    EditBox edit = new EditBox(font, fieldStartX, y, fieldWidth, 20, Component.empty());
                    edit.setValue("{}");
                    row.widget = edit;
                    row.valueSupplier = () -> {
                        try {
                            return JsonParser.parseString(edit.getValue().trim());
                        } catch (Exception e) {
                            return JsonNull.INSTANCE;
                        }
                    };
                }

                // 标签
                FieldLabel label = new FieldLabel(font, 10, y, labelWidth, 20, row.label);
                addRenderableWidget(label);
                addRenderableWidget(row.widget);
                fieldRows.add(row);
                y += 24;
            }

            // 底部按钮：跟在最后一行之后（以前是 max(y+10, height-32)，字段多时会压住最后几行）
            int btnY = Math.min(Math.max(y + 10, 32), Math.max(32, height - 32));
            SreButton confirmBtn = SreButton.create(Component.translatable("sre.map_helper.confirm"), b -> {
                JsonObject json = new JsonObject();
                for (FieldRow row : fieldRows) {
                    json.add(row.field.getName(), row.valueSupplier.get());
                }
                String jsonStr = json.toString();
                ctx.sendOnly("sre:area_manager add " + path + " " + (jsonStr));
                if (onSuccess != null)
                    onSuccess.run();
                onClose();
            }).bounds(width / 2 - 105, btnY, 100, 20).build();
            addRenderableWidget(confirmBtn);

            SreButton cancelBtn = SreButton.create(Component.translatable("sre.map_helper.cancel"), b -> {
                onClose();
            }).bounds(width / 2 + 5, btnY, 100, 20).build();
            addRenderableWidget(cancelBtn);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            renderBackground(g, mouseX, mouseY, partial);
            g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
            super.render(g, mouseX, mouseY, partial);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(parent);
        }

        private static boolean shouldShowFieldStatic(Field field) {
            if (field.isAnnotationPresent(Expose.class)) {
                Expose expose = field.getAnnotation(Expose.class);
                return expose.serialize() && expose.deserialize();
            }
            int mod = field.getModifiers();
            return !Modifier.isStatic(mod) && !Modifier.isTransient(mod);
        }
    }
    // ── Inner classes ───────────────────────────────────────────────

    /**
     * 配置项节点
     */
    private class SettingsEntry {
        String path;
        Field field;
        Object parentObject;
        int depth;
        boolean expanded = false;
        List<SettingsEntry> children = new ArrayList<>();
        String displayName;
        String displayNameKey; // 保存用于工具提示的键
        Object currentValue;
        /** 搜索索引：原文 + 全拼 + 拼音首字母（小写，建树时算一次） */
        String searchIndex = "";

        SettingsEntry(String path, Field field, Object parent, int depth) {
            this.path = path;
            this.field = field;
            this.parentObject = parent;
            this.depth = depth;
            if (parentObject == rootSettings) {
                this.displayNameKey = "sre.map_helper.settings." + path;
            } else {
                String className = field.getDeclaringClass().getSimpleName();
                this.displayNameKey = "sre.map_helper.settings.class." + className + "." + field.getName();
            }
            this.displayName = Component.translatableWithFallback(displayNameKey, field.getName()).getString();
            // 原文（名称 / 路径 / 字段名）+ 拼音：于是「会议」「huiyi」「hy」都能搜到
            String raw = (this.displayName + " " + path + " " + field.getName()).toLowerCase(Locale.ROOT);
            this.searchIndex = raw + "|" + PinYinUtils.toSearchablePinyin(this.displayName)
                    + "|" + PinYinUtils.toPinyinInitials(this.displayName);
            updateValue();
        }

        void updateValue() {
            try {
                field.setAccessible(true);
                currentValue = field.get(parentObject);
            } catch (IllegalAccessException e) {
                currentValue = null;
            }
        }

        boolean isLeaf() {
            return children.isEmpty();
        }
    }

    private static class CategoryHeaderEntry {
        final String displayName;
        final String categoryId;
        /** 这个分类下一共有多少项（折叠时显示，免得不知道藏了什么）。 */
        final int count;

        CategoryHeaderEntry(String displayName, String categoryId, int count) {
            this.displayName = displayName;
            this.categoryId = categoryId;
            this.count = count;
        }
    }

    /** 一个分类卡片的范围：构建时记下，渲染时垫在这一块控件下面（与编辑器的卡片同一套观感）。 */
    private static class CategoryCard {
        final String categoryId;
        final int x;
        final int y;
        final int w;
        /** 折叠的分类不画卡片框（只留可点的标题行），免得堆成一排「奇怪的条状」。 */
        final boolean collapsed;
        int h;

        CategoryCard(String categoryId, int x, int y, int w, boolean collapsed) {
            this.categoryId = categoryId;
            this.x = x;
            this.y = y;
            this.w = w;
            this.collapsed = collapsed;
        }
    }

    private static class CategoryLabel extends AbstractWidget {
        private final String text;
        private final Runnable onPress;
        private final boolean[] clipTooltipShown = new boolean[1];

        public CategoryLabel(Font font, int x, int y, int width, int height, String text) {
            this(font, x, y, width, height, text, null);
        }

        public CategoryLabel(Font font, int x, int y, int width, int height, String text, Runnable onPress) {
            super(x, y, width, height, Component.literal(text));
            this.text = text;
            this.onPress = onPress;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (onPress != null) {
                onPress.run();
            }
        }

        private void updateClipTooltip(boolean fits, String full) {
            applyClipTooltip(this, fits, full, clipTooltipShown);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            // 卡片头观感（与编辑器一致）：整条可点、悬停高亮，金色粗体标题 + ▾/▸ 折叠标记
            if (isHovered() && onPress != null) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), SREPanelStyle.ROW_HOVER);
            }
            int textY = getY() + (getHeight() - font.lineHeight) / 2;
            String shown = MapUiGraphics.clip(font, text, Math.max(8, getWidth() - 10));
            updateClipTooltip(shown.equals(text), text);
            g.drawString(font,
                    Component.literal(shown).withStyle(Style.EMPTY.withColor(SREPanelStyle.GOLD).withBold(true)),
                    getX() + 5, textY, SREPanelStyle.GOLD, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }

    private static class FieldLabel extends AbstractWidget {
        private final String text;
        private final boolean[] clipTooltipShown = new boolean[1];

        public FieldLabel(Font font, int x, int y, int width, int height, String text) {
            super(x, y, width, height, Component.literal(text));
            this.text = text;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            // 放不下就省略号截断：长标签以前会直接画到右边控件底下（zh_cn 里有 180px 的标签）
            String shown = MapUiGraphics.clip(font, text, Math.max(8, getWidth()));
            updateClipTooltip(shown.equals(text), text);
            g.drawString(font, shown, getX(), getY() + 4, 0xCCDDEE, false);
        }

        private void updateClipTooltip(boolean fits, String full) {
            applyClipTooltip(this, fits, full, clipTooltipShown);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }
}