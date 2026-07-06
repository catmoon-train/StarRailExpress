package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import com.google.gson.Gson;
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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.lang.reflect.Field;
import java.util.*;

public class AllSettingsModule implements TabModule {
    private static final Gson GSON = new Gson();
    private List<SettingsEntry> allSettingsEntries = new ArrayList<>();
    private int totalContentHeight = 0;

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.all");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        allSettingsEntries.clear();
        AreasWorldComponent comp = SREClient.areaComponent;
        if (comp == null) {
            totalContentHeight = 0;
            return;
        }
        Object settings = comp.areasSettings;
        if (settings == null) {
            totalContentHeight = 0;
            return;
        }

        // Build root entries
        Class<?> clazz = settings.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (!shouldShowField(field))
                continue;
            SettingsEntry root = new SettingsEntry(field.getName(), field, settings, 0);
            if (shouldExpandObject(root.currentValue))
                expandObject(root);
            allSettingsEntries.add(root);
        }

        // Create mixed list with category headers
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
        return null;
    }

    private String getCategoryDisplayName(String categoryId) {
        if (categoryId == null)
            categoryId = "default";
        return Component.translatableWithFallback("sre.map_helper.settings.category." + categoryId, categoryId)
                .getString();
    }

    private boolean shouldExpandObject(Object obj) {
        if (obj == null)
            return false;
        Class<?> clazz = obj.getClass();
        return !clazz.isPrimitive() && !clazz.isEnum() && clazz != String.class &&
                !Collection.class.isAssignableFrom(clazz) && !Map.class.isAssignableFrom(clazz) &&
                !Number.class.isAssignableFrom(clazz) && !Boolean.class.isAssignableFrom(clazz);
    }

    private void expandObject(SettingsEntry parent) {
        Object obj = parent.currentValue;
        if (obj == null)
            return;
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (!shouldShowField(field))
                continue;
            SettingsEntry child = new SettingsEntry(parent.path + "." + field.getName(), field, obj, parent.depth + 1);
            if (shouldExpandObject(child.currentValue))
                expandObject(child);
            parent.children.add(child);
        }
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
                if (entry.expanded && !entry.children.isEmpty()) {
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
            if (entry.expanded && !entry.children.isEmpty()) {
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

    private static class EnumValueLabel extends AbstractWidget {
        private String text;

        public EnumValueLabel(Font font, int x, int y, int width, int height, String initialText) {
            super(x, y, width, height, Component.literal(initialText));
            this.text = initialText;
        }

        public void setText(String newText) {
            this.text = newText;
            setMessage(Component.literal(newText)); // 同步无障碍消息
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
        int rightEdge = layout.panelLeftX + layout.panelWidth - layout.gutter;

        int leftX = layout.leftColumnX() + entry.depth * 12;
        int labelWidth = Math.min(100, (layout.contentWidth() - entry.depth * 12) / 3);
        int gap = 6;
        Class<?> type = entry.field.getType();
        Object value = entry.currentValue;
        int usedHeight = 30;

        FieldLabel label = new FieldLabel(layout.font, leftX, y, labelWidth, 20, entry.displayName);
        placements.add(new WidgetPlacement(label, y));

        int controlX = leftX + labelWidth + gap;
        int remainingWidth = layout.contentWidth() - (controlX - layout.leftColumnX()) - 6;

        if (entry.isLeaf()) {
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
            } else if (type == String.class || isNumberType(type)) {
                int inputWidth = Math.max(70, remainingWidth - 40 - 30 - 6);
                EditBox input = new EditBox(layout.font, controlX, y, inputWidth, 20, Component.empty());
                input.setValue(value != null ? value.toString() : "");
                input.setMaxLength(50);
                placements.add(new WidgetPlacement(input, y));
                ModernButton modifyBtn = ModernButton.builder(Component.translatable("sre.map_helper.modify"), b -> {
                    String val = input.getValue().trim();
                    if (!val.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + ctx.quoteCommandArgument(val));
                }).bounds(controlX + inputWidth + gap, y, 40, 20).accentBar(AccentSide.BOTTOM).build();
                ModernButton viewBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.view"),
                                b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(controlX + inputWidth + gap + 44, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(modifyBtn, y));
                placements.add(new WidgetPlacement(viewBtn, y));
            } else if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                if (constants == null || constants.length == 0) {
                    // 无枚举常量，仅显示查看按钮（右对齐）
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
                    int totalW = arrowBtnW + gapBtn + displayW + gapBtn + arrowBtnW;
                    int startX = rightEdge - totalW;

                    // 当前索引的包装（数组以便在 lambda 中修改）
                    final int[] selectedIndex = { currentIdx };

                    // 枚举值显示文本获取函数
                    java.util.function.Function<Integer, String> getDisplayName = idx -> {
                        String name = ((Enum<?>) constants[idx]).name();
                        String key = "sre.map_helper.settings." + entry.field.getName() + "." + name;
                        return Component.translatableWithFallback(key, name).getString();
                    };

                    // 中间显示标签
                    EnumValueLabel displayLabel = new EnumValueLabel(layout.font, startX + arrowBtnW + gapBtn, y,
                            displayW, 20,
                            getDisplayName.apply(selectedIndex[0]));

                    // 左箭头按钮
                    ModernButton leftArrow = ModernButton.builder(Component.literal("<-"), b -> {
                        int idx = selectedIndex[0];
                        int newIdx = (idx - 1 + constants.length) % constants.length;
                        String newName = ((Enum<?>) constants[newIdx]).name();
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + newName);
                        selectedIndex[0] = newIdx;
                        displayLabel.setText(getDisplayName.apply(newIdx));
                    }).bounds(startX, y, arrowBtnW, 20).accentBar(AccentSide.LEFT).build();

                    // 右箭头按钮
                    ModernButton rightArrow = ModernButton.builder(Component.literal("->"), b -> {
                        int idx = selectedIndex[0];
                        int newIdx = (idx + 1) % constants.length;
                        String newName = ((Enum<?>) constants[newIdx]).name();
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + newName);
                        selectedIndex[0] = newIdx;
                        displayLabel.setText(getDisplayName.apply(newIdx));
                    }).bounds(startX + arrowBtnW + gapBtn + displayW + gapBtn, y, arrowBtnW, 20)
                            .accentBar(AccentSide.RIGHT).build();

                    // 为箭头按钮添加 Tooltip（若存在）
                    String tooltipKey = "sre.map_helper.settings." + entry.field.getName() + ".@tooltip";
                    if (I18n.exists(tooltipKey)) {
                        leftArrow.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                        rightArrow.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                    }

                    placements.add(new WidgetPlacement(leftArrow, y));
                    placements.add(new WidgetPlacement(displayLabel, y));
                    placements.add(new WidgetPlacement(rightArrow, y));
                }
            } else if (Collection.class.isAssignableFrom(type)) {
                int x = controlX;
                int inputWidth = Math.min(70, (remainingWidth - 35 - 55 - 35 - 35 - 30 - 5 * gap) / 2);
                EditBox addInput = new EditBox(layout.font, x, y, inputWidth, 20,
                        Component.translatable("sre.map_helper.value"));
                placements.add(new WidgetPlacement(addInput, y));
                ModernButton addBtn = ModernButton.builder(Component.translatable("sre.map_helper.add"), b -> {
                    String val = addInput.getValue().trim();
                    if (!val.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " add " + ctx.quoteCommandArgument(val));
                }).bounds(x + inputWidth + gap, y, 35, 20).accentBar(AccentSide.LEFT).build();
                placements.add(new WidgetPlacement(addBtn, y));
                int x2 = x + inputWidth + gap + 35 + gap;
                EditBox removeInput = new EditBox(layout.font, x2, y,
                        Math.min(55, remainingWidth - inputWidth - 35 - 35 - 30 - 4 * gap), 20,
                        Component.translatable("sre.map_helper.value"));
                placements.add(new WidgetPlacement(removeInput, y));
                ModernButton removeBtn = ModernButton.builder(Component.translatable("sre.map_helper.remove"), b -> {
                    String val = removeInput.getValue().trim();
                    if (!val.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " remove " + ctx.quoteCommandArgument(val));
                }).bounds(x2 + removeInput.getWidth() + gap, y, 35, 20).accentBar(AccentSide.RIGHT).build();
                placements.add(new WidgetPlacement(removeBtn, y));
                int x3 = x2 + removeInput.getWidth() + gap + 35 + gap;
                ModernButton clearBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.clear"),
                                b -> ctx.sendOnly("sre:area_manager set " + entry.path + " clear"))
                        .bounds(x3, y, 35, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(clearBtn, y));
                ModernButton viewBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.view"),
                                b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(x3 + 35 + gap, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(viewBtn, y));
            } else if (Map.class.isAssignableFrom(type)) {
                int inputWidth = Math.min(120, remainingWidth - 40 - 30 - 2 * gap);
                EditBox mapInput = new EditBox(layout.font, controlX, y, inputWidth, 20,
                        Component.translatable("sre.map_helper.json"));
                mapInput.setValue(value != null ? GSON.toJson(value) : "{}");
                placements.add(new WidgetPlacement(mapInput, y));
                ModernButton modifyBtn = ModernButton.builder(Component.translatable("sre.map_helper.modify"), b -> {
                    String json = mapInput.getValue().trim();
                    if (!json.isEmpty())
                        ctx.sendOnly("sre:area_manager set " + entry.path + " " + ctx.quoteCommandArgument(json));
                }).bounds(controlX + inputWidth + gap, y, 40, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(modifyBtn, y));
                ModernButton viewBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.view"),
                                b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(controlX + inputWidth + gap + 44, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(viewBtn, y));
            }
        } else {
            ModernButton toggleBtn = ModernButton.builder(Component.literal(entry.expanded ? "▾" : "▸"), b -> {
                entry.expanded = !entry.expanded;
                ctx.refreshScreen();
            }).bounds(controlX, y, 20, 20).accentBar(AccentSide.BOTTOM).build();
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

    // ── Inner classes ───────────────────────────────────────────────
    private class SettingsEntry {
        String path;
        Field field;
        Object parentObject;
        int depth;
        boolean expanded = false;
        List<SettingsEntry> children = new ArrayList<>();
        String displayName;
        Object currentValue;

        SettingsEntry(String path, Field field, Object parent, int depth) {
            this.path = path;
            this.field = field;
            this.parentObject = parent;
            this.depth = depth;
            this.displayName = Component
                    .translatableWithFallback("sre.map_helper.settings." + field.getName(), field.getName())
                    .getString();
            getCategoryId(field);
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
            int textY = getY() + 4; // 文本绘制的 Y 坐标（与原来一致）
            int textCenterY = textY + font.lineHeight / 2; // 文字垂直中心点
            int barHeight = 4; // 左侧色条高度（可调整）
            int barTop = textCenterY - barHeight / 2;
            int barBottom = textCenterY + barHeight / 2;

            // 绘制左侧蓝色竖条，垂直居中对齐文字
            g.fill(getX(), barTop, getX() + 4, barBottom, 0xFF5577CC);

            // 绘制分类标题文本
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