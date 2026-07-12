package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import io.wifi.ConfigCompact.annotation.Category;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

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
    private boolean entriesBuilt = false;

    // 保存根对象，用于判断字段是否属于根对象
    private Object rootSettings;

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.all");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        if (!entriesBuilt) {
            buildEntryTree();
            entriesBuilt = true;
        }

        List<Object> flatList = new ArrayList<>();
        String lastCategory = null;
        for (SettingsEntry entry : allSettingsEntries) {
            String cat = getCategoryId(entry.field);
            if (!Objects.equals(cat, lastCategory)) {
                flatList.add(new CategoryHeaderEntry(getCategoryDisplayName(cat), cat));
                lastCategory = cat;
            }
            flatList.add(entry);
        }

        totalContentHeight = createWidgetsForMixedEntries(layout, ctx, placements, flatList, 0);
    }

    private void buildEntryTree() {
        allSettingsEntries.clear();
        AreasWorldComponent comp = SREClient.areaComponent;
        if (comp == null)
            return;
        rootSettings = comp.areasSettings;
        if (rootSettings == null)
            return;

        Class<?> clazz = rootSettings.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!shouldShowField(field))
                continue;
            SettingsEntry root = new SettingsEntry(field.getName(), field, rootSettings, 0);
            if (shouldExpandObject(root.currentValue))
                expandObject(root);
            allSettingsEntries.add(root);
        }
    }

    @Override
    public int getContentHeight() {
        return totalContentHeight;
    }

    // ── Helpers ─────────────────────────────────────────────────────
    private boolean shouldShowField(Field field) {
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
            if (obj instanceof CategoryHeaderEntry header) {
                currentY += createWidgetsForCategoryHeader(layout, placements, header, currentY);
            } else if (obj instanceof SettingsEntry entry) {
                currentY += createWidgetsForEntry(layout, ctx, placements, entry, currentY);
                // 如果是展开的集合且元素为内置类型，则动态生成子行（不依赖 children）
                if (entry.expanded && isCollectionWithBuiltinElements(entry)) {
                    currentY = createWidgetsForCollectionChildren(layout, ctx, placements, entry, currentY);
                } else if (entry.expanded && !entry.children.isEmpty()) {
                    // 普通对象的子节点
                    currentY = createWidgetsForEntries(layout, ctx, placements, entry.children, currentY);
                }
            }
        }
        return currentY;
    }

    private int createWidgetsForEntries(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements,
            List<SettingsEntry> entries, int yOffset) {
        int currentY = yOffset;
        for (SettingsEntry entry : entries) {
            currentY += createWidgetsForEntry(layout, ctx, placements, entry, currentY);
            if (entry.expanded && isCollectionWithBuiltinElements(entry)) {
                currentY = createWidgetsForCollectionChildren(layout, ctx, placements, entry, currentY);
            } else if (entry.expanded && !entry.children.isEmpty()) {
                currentY = createWidgetsForEntries(layout, ctx, placements, entry.children, currentY);
            }
        }
        return currentY;
    }

    private int createWidgetsForCategoryHeader(LayoutContext layout, List<WidgetPlacement> placements,
            CategoryHeaderEntry header, int y) {
        int leftX = layout.leftColumnX();
        int width = layout.contentWidth();
        int height = 24;
        CategoryLabel label = new CategoryLabel(layout.font, leftX, y, width, height, header.displayName);
        placements.add(new WidgetPlacement(label, y));
        return height;
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
        int rightEdge = layout.panelLeftX + layout.panelWidth - layout.gutter - 4;
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
            int startX = rightEdge - totalW;

            EnumValueLabel enumDisplay = new EnumValueLabel(layout.font, startX + arrowBtnW + gapBtn, currentY,
                    displayW, 20, displayText);
            // 初始tooltip
            String initialTooltipKey = "sre.map_helper.settings." + entry.path + "." + initialEnumName + ".@tooltip";
            if (I18n.exists(initialTooltipKey)) {
                enumDisplay.setTooltip(Tooltip.create(Component.translatable(initialTooltipKey)));
            }
            placements.add(new WidgetPlacement(enumDisplay, currentY));

            ModernButton leftArrow = ModernButton.builder(Component.literal("<-"), b -> {
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
            }).bounds(startX, currentY, arrowBtnW, 20).accentBar(AccentSide.LEFT).build();
            placements.add(new WidgetPlacement(leftArrow, currentY));

            ModernButton rightArrow = ModernButton.builder(Component.literal("->"), b -> {
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
                    .accentBar(AccentSide.RIGHT).build();
            placements.add(new WidgetPlacement(rightArrow, currentY));

            ModernButton addBtn = ModernButton.builder(
                    Component.translatable("sre.map_helper.add"),
                    b -> {
                        String name = ((Enum<?>) constants[addSelectedIndex[0]]).name();
                        ctx.sendOnly("sre:area_manager add " + entry.path + " " + name);
                    })
                    .bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + gapBtn, currentY, 40, 20)
                    .accentBar(AccentSide.BOTTOM).build();
            placements.add(new WidgetPlacement(addBtn, currentY));
        } else {
            int inputWidth = Math.min(100, remainingWidth - 40 - gap);
            EditBox addInput = new EditBox(layout.font, controlStartX, currentY, inputWidth, 20,
                    Component.translatable("sre.map_helper.value"));
            if (I18n.exists(tooltipKey)) {
                addInput.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            }
            placements.add(new WidgetPlacement(addInput, currentY));

            int addBtnX = controlStartX + inputWidth + gap;
            ModernButton addBtn = ModernButton.builder(
                    Component.translatable("sre.map_helper.add"),
                    b -> {
                        String val = addInput.getValue().trim();
                        if (!val.isEmpty())
                            ctx.sendOnly("sre:area_manager add " + entry.path + " " + (val));
                    })
                    .bounds(addBtnX, currentY, 40, 20)
                    .accentBar(AccentSide.LEFT).build();
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
            int startX = rightEdge - totalW;

            EnumValueLabel enumDisplay = new EnumValueLabel(layout.font, startX + arrowBtnW + gapBtn, currentY,
                    displayW, 20, displayText);
            String initialTooltipKey = "sre.map_helper.settings." + entry.path + "." + initialEnumName + ".@tooltip";
            if (I18n.exists(initialTooltipKey)) {
                enumDisplay.setTooltip(Tooltip.create(Component.translatable(initialTooltipKey)));
            }
            placements.add(new WidgetPlacement(enumDisplay, currentY));

            ModernButton leftArrow = ModernButton.builder(Component.literal("<-"), b -> {
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
            }).bounds(startX, currentY, arrowBtnW, 20).accentBar(AccentSide.LEFT).build();
            placements.add(new WidgetPlacement(leftArrow, currentY));

            ModernButton rightArrow = ModernButton.builder(Component.literal("->"), b -> {
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
                    .accentBar(AccentSide.RIGHT).build();
            placements.add(new WidgetPlacement(rightArrow, currentY));

            ModernButton deleteBtn = ModernButton.builder(
                    Component.translatable("sre.map_helper.remove"),
                    b -> {
                        String name = ((Enum<?>) constants[deleteSelectedIndex[0]]).name();
                        ctx.sendOnly("sre:area_manager remove " + entry.path + " " + name);
                    })
                    .bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + gapBtn, currentY, 40, 20)
                    .accentBar(AccentSide.BOTTOM).build();
            placements.add(new WidgetPlacement(deleteBtn, currentY));
        } else {
            int inputWidth = Math.min(100, deleteRemainingWidth - 40 - gap);
            EditBox deleteInput = new EditBox(layout.font, deleteControlStartX, currentY, inputWidth, 20,
                    Component.translatable("sre.map_helper.value"));
            if (I18n.exists(tooltipKey)) {
                deleteInput.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            }
            placements.add(new WidgetPlacement(deleteInput, currentY));

            int deleteBtnX = deleteControlStartX + inputWidth + gap;
            ModernButton deleteBtn = ModernButton.builder(
                    Component.translatable("sre.map_helper.remove"),
                    b -> {
                        String val = deleteInput.getValue().trim();
                        if (!val.isEmpty())
                            ctx.sendOnly("sre:area_manager remove " + entry.path + " " + (val));
                    })
                    .bounds(deleteBtnX, currentY, 40, 20)
                    .accentBar(AccentSide.RIGHT).build();
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
        int rightEdge = layout.panelLeftX + layout.panelWidth - layout.gutter - 4;
        int leftX = layout.leftColumnX() + entry.depth * 12;
        int labelWidth = Math.min(140, (layout.contentWidth() - entry.depth * 12) / 3);
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

        int controlX = leftX + labelWidth + gap;
        int remainingWidth = layout.contentWidth() - (controlX - layout.leftColumnX()) - 6;

        // 处理集合类型（优先判断）
        if (Collection.class.isAssignableFrom(type)) {
            // 区分自定义元素和内置元素
            if (isCollectionWithCustomElements(entry)) {
                // 自定义元素列表：Add, JSON, Clear, View
                int btnW = 30;
                int gapBtn = 3;
                int totalWidth = btnW * 4 + gapBtn * 3; // 30*4 + 3*3 = 129
                int startX = rightEdge - totalWidth;

                // Add 按钮：打开表单添加
                ModernButton addBtn = ModernButton.builder(
                        Component.translatableWithFallback("sre.map_helper.add.form", "Add"),
                        b -> {
                            Class<?> elemType = getElementType(entry.field);
                            if (elemType != null) {
                                Minecraft.getInstance().setScreen(
                                        new FormAddScreen(entry.path, ctx, elemType,
                                                () -> ctx.requestModuleRefresh(), ctx.screen()));
                            }
                        })
                        .bounds(startX, y, btnW, 20).accentBar(AccentSide.LEFT).build();
                placements.add(new WidgetPlacement(addBtn, y));

                // JSON 按钮：打开 JSON 输入对话框
                ModernButton jsonBtn = ModernButton.builder(
                        Component.literal("JSON"),
                        b -> Minecraft.getInstance().setScreen(
                                new JsonInputScreen(entry.path, ctx,
                                        () -> ctx.requestModuleRefresh(), ctx.screen())))
                        .bounds(startX + btnW + gapBtn, y, btnW, 20).accentBar().build();
                placements.add(new WidgetPlacement(jsonBtn, y));

                // Clear 按钮
                ModernButton clearBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.clear"),
                        b -> ctx.sendOnly("sre:area_manager clear " + entry.path))
                        .bounds(startX + (btnW + gapBtn) * 2, y, btnW, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(clearBtn, y));

                // View 按钮
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(startX + (btnW + gapBtn) * 3, y, btnW, 20).accentBar(AccentSide.BOTTOM)
                        .build();
                placements.add(new WidgetPlacement(viewBtn, y));

            } else {
                // 内置元素列表：可展开，右侧显示 展开/收起, Clear, View
                int btnW = 50;
                int viewW = 30;
                int gapBtn = 4;
                int totalWidth = btnW + gapBtn + 40 + gapBtn + viewW;
                int startX = rightEdge - totalWidth;

                // 展开/收起按钮
                ModernButton toggleBtn = ModernButton.builder(
                        Component.translatable(entry.expanded ? "sre.map_helper.expandable.unexpand"
                                : "sre.map_helper.expandable.expand"),
                        b -> {
                            entry.expanded = !entry.expanded;
                            ctx.requestModuleRefresh();
                        })
                        .bounds(startX, y, btnW, 20).accentBar().build();
                placements.add(new WidgetPlacement(toggleBtn, y));
                if (I18n.exists(tooltipKey)) {
                    toggleBtn.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                }

                // Clear 按钮
                ModernButton clearBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.clear"),
                        b -> ctx.sendOnly("sre:area_manager clear " + entry.path))
                        .bounds(startX + btnW + gapBtn, y, 40, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(clearBtn, y));

                // View 按钮
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(startX + btnW + gapBtn + 40 + gapBtn, y, viewW, 20).accentBar(AccentSide.BOTTOM)
                        .build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            return usedHeight;
        }

        // ----- 其他类型（非集合）的处理 -----
        if (entry.isLeaf()) {
            // 布尔类型
            if (type == boolean.class || type == Boolean.class) {
                int btnW1 = 50, btnW2 = 50, btnW3 = 30;
                int gapBtn = 4;
                int totalControlWidth = btnW1 + gapBtn + btnW2 + gapBtn + btnW3;
                int startX = rightEdge - totalControlWidth;

                ModernButton enableBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.set_true_null"),
                        b -> ctx.sendOnly("sre:area_manager set " + entry.path + " true"))
                        .bounds(startX, y, btnW1, 20).accentBar(AccentSide.LEFT).build();
                ModernButton disableBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.set_false_null"),
                        b -> ctx.sendOnly("sre:area_manager set " + entry.path + " false"))
                        .bounds(startX + btnW1 + gapBtn, y, btnW2, 20).accentBar(AccentSide.RIGHT).build();
                ModernButton viewBtn = ModernButton.builder(
                        Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(startX + btnW1 + gapBtn + btnW2 + gapBtn, y, btnW3, 20).accentBar(AccentSide.BOTTOM)
                        .build();

                placements.add(new WidgetPlacement(enableBtn, y));
                placements.add(new WidgetPlacement(disableBtn, y));
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            // 字符串 & 数字
            else if (type == String.class || isNumberType(type)) {
                int inputWidth = Math.max(70, remainingWidth - 120);
                EditBox input = new EditBox(layout.font, rightEdge - inputWidth - 40 - 30 - gap * 2, y, inputWidth, 20,
                        Component.empty());
                input.setValue(value != null ? value.toString() : "");
                input.setMaxLength(50);
                if (I18n.exists(tooltipKey)) {
                    input.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                }
                placements.add(new WidgetPlacement(input, y));
                ModernButton modifyBtn = ModernButton.builder(Component.translatable("sre.map_helper.modify"), b -> {
                    String val = input.getValue().trim();
                    if (!val.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + (val));
                }).bounds(rightEdge - 30 - 40 - gap, y, 40, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(modifyBtn, y));
                ModernButton viewBtn = ModernButton.builder(Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(rightEdge - 30, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            // 枚举
            else if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                if (constants == null || constants.length == 0) {
                    ModernButton viewBtn = ModernButton.builder(Component.translatable("sre.map_helper.view"),
                            b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                            .bounds(rightEdge - 30, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
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

                    int arrowBtnW = 20, displayW = 80, gapBtn = 4;
                    int totalW = arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW + 30 + gap;
                    int startX = rightEdge - totalW;

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

                    ModernButton leftArrow = ModernButton.builder(Component.literal("<-"), b -> {
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
                    }).bounds(startX, y, arrowBtnW, 20).accentBar(AccentSide.LEFT).build();

                    ModernButton rightArrow = ModernButton.builder(Component.literal("->"), b -> {
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
                            .accentBar(AccentSide.RIGHT).build();

                    ModernButton viewBtn = ModernButton.builder(Component.translatable("sre.map_helper.view"),
                            b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                            .bounds(rightEdge - 30, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                    placements.add(new WidgetPlacement(viewBtn, y));
                    placements.add(new WidgetPlacement(leftArrow, y));
                    placements.add(new WidgetPlacement(displayLabel, y));
                    placements.add(new WidgetPlacement(rightArrow, y));
                }
            }
            // Map 类型
            else if (Map.class.isAssignableFrom(type)) {
                int inputWidth = Math.min(120, remainingWidth - 40 - 30 - 2 * gap);
                EditBox mapInput = new EditBox(layout.font, controlX, y, inputWidth, 20,
                        Component.translatable("sre.map_helper.json"));
                mapInput.setValue(value != null ? GSON.toJson(value) : "{}");
                if (I18n.exists(tooltipKey)) {
                    mapInput.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                }
                placements.add(new WidgetPlacement(mapInput, y));
                ModernButton modifyBtn = ModernButton.builder(Component.translatable("sre.map_helper.modify"), b -> {
                    String json = mapInput.getValue().trim();
                    if (!json.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + (json));
                }).bounds(controlX + inputWidth + gap, y, 40, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(modifyBtn, y));
                ModernButton viewBtn = ModernButton.builder(Component.translatable("sre.map_helper.view"),
                        b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(controlX + inputWidth + gap + 44, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
            // 其他类型（自定义对象）但已经是叶子？理论上自定义对象应展开，所以不会到这里
        } else {
            // 非叶子节点（普通自定义对象），仅显示展开/收起按钮
            ModernButton toggleBtn = ModernButton.builder(
                    Component.translatable(!entry.expanded ? "sre.map_helper.expandable.expand"
                            : "sre.map_helper.expandable.unexpand"),
                    b -> {
                        entry.expanded = !entry.expanded;
                        ctx.requestModuleRefresh();
                    }).bounds(rightEdge - 50, y, 50, 20).accentBar().build();
            if (I18n.exists(tooltipKey)) {
                toggleBtn.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
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
        private ModernButton confirmBtn;
        private ModernButton cancelBtn;
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

            confirmBtn = ModernButton.builder(
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
                    .accentBar(AccentSide.LEFT).build();
            addRenderableWidget(confirmBtn);

            cancelBtn = ModernButton.builder(
                    Component.translatable("sre.map_helper.cancel"),
                    b -> onClose())
                    .bounds(centerX + 5, centerY + 10, 100, 20)
                    .accentBar(AccentSide.RIGHT).build();
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
                    edit.setValue("false");
                    edit.setMaxLength(5);
                    row.widget = edit;
                    row.valueSupplier = () -> {
                        String val = edit.getValue().trim();
                        return new JsonPrimitive("true".equalsIgnoreCase(val));
                    };
                } else if (type.isEnum()) {
                    Object[] constants = type.getEnumConstants();
                    if (constants != null && constants.length > 0) {
                        final int[] idx = { 0 };
                        ModernButton enumBtn = ModernButton.builder(
                                Component.literal(((Enum<?>) constants[0]).name()), b -> {
                                    idx[0] = (idx[0] + 1) % constants.length;
                                    b.setMessage(Component.literal(((Enum<?>) constants[idx[0]]).name()));
                                }).bounds(fieldStartX, y, Math.min(120, fieldWidth), 20).accentBar().build();
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

            // 底部按钮
            int btnY = Math.max(y + 10, height - 32);
            ModernButton confirmBtn = ModernButton.builder(Component.translatable("sre.map_helper.confirm"), b -> {
                JsonObject json = new JsonObject();
                for (FieldRow row : fieldRows) {
                    json.add(row.field.getName(), row.valueSupplier.get());
                }
                String jsonStr = json.toString();
                ctx.sendOnly("sre:area_manager add " + path + " " + (jsonStr));
                if (onSuccess != null)
                    onSuccess.run();
                onClose();
            }).bounds(width / 2 - 105, btnY, 100, 20).accentBar(AccentSide.LEFT).build();
            addRenderableWidget(confirmBtn);

            ModernButton cancelBtn = ModernButton.builder(Component.translatable("sre.map_helper.cancel"), b -> {
                onClose();
            }).bounds(width / 2 + 5, btnY, 100, 20).accentBar(AccentSide.RIGHT).build();
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
        String displayName;

        CategoryHeaderEntry(String displayName, String categoryId) {
            this.displayName = displayName;
        }
    }

    private static class CategoryLabel extends AbstractWidget {
        private final String text;

        public CategoryLabel(Font font, int x, int y, int width, int height, String text) {
            super(x, y, width, height, Component.literal(text));
            this.text = text;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int textY = getY() + 4;
            int textCenterY = textY + font.lineHeight / 2;
            int barHeight = 4;
            int barTop = textCenterY - barHeight / 2;
            int barBottom = textCenterY + barHeight / 2;
            g.fill(getX(), barTop, getX() + 4, barBottom, 0xFF5577CC);
            g.drawString(font,
                    Component.literal(text).withStyle(Style.EMPTY.withColor(0xFFAA00).withBold(true)),
                    getX() + 8, textY, 0xFFFFFF, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }

    private static class FieldLabel extends AbstractWidget {
        private final String text;

        public FieldLabel(Font font, int x, int y, int width, int height, String text) {
            super(x, y, width, height, Component.literal(text));
            this.text = text;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.drawString(Minecraft.getInstance().font, text, getX(), getY() + 4, 0xCCDDEE, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }
}