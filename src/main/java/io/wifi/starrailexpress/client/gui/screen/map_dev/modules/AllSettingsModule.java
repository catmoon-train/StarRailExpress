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
import net.minecraft.client.gui.narration.NarrationElementOutput;
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

    private int createWidgetsForEntry(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements,
            SettingsEntry entry, int y) {
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
                ModernButton enableBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.set_true", Component.literal("")),
                                b -> ctx.sendOnly("sre:area_manager set " + entry.path + " true"))
                        .bounds(controlX, y, 50, 20).accentBar(AccentSide.LEFT).build();
                ModernButton disableBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.set_false", Component.literal("")),
                                b -> ctx.sendOnly("sre:area_manager set " + entry.path + " false"))
                        .bounds(controlX + 54, y, 50, 20).accentBar(AccentSide.RIGHT).build();
                ModernButton viewBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.view"),
                                b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(controlX + 108, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(enableBtn, y));
                placements.add(new WidgetPlacement(disableBtn, y));
                placements.add(new WidgetPlacement(viewBtn, y));
            } else if (type == String.class || Number.class.isAssignableFrom(type)) {
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
                // Simplified enum rendering: show current value and a button to cycle or select
                EditBox enumView = new EditBox(layout.font, controlX, y, remainingWidth - 34, 20, Component.empty());
                enumView.setValue(value.toString());
                enumView.setEditable(false);
                placements.add(new WidgetPlacement(enumView, y));
                ModernButton viewBtn = ModernButton
                        .builder(Component.translatable("sre.map_helper.view"),
                                b -> ctx.sendOnly("sre:area_manager get " + entry.path))
                        .bounds(controlX + remainingWidth - 30, y, 30, 20).accentBar(AccentSide.BOTTOM).build();
                placements.add(new WidgetPlacement(viewBtn, y));
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

    // ── Inner classes ───────────────────────────────────────────────
    private class SettingsEntry {
        String path;
        Field field;
        Object parentObject;
        int depth;
        boolean expanded = false;
        List<SettingsEntry> children = new ArrayList<>();
        String displayName;
        String categoryId;
        Object currentValue;

        SettingsEntry(String path, Field field, Object parent, int depth) {
            this.path = path;
            this.field = field;
            this.parentObject = parent;
            this.depth = depth;
            this.displayName = Component
                    .translatableWithFallback("sre.map_helper.settings." + field.getName(), field.getName())
                    .getString();
            this.categoryId = getCategoryId(field);
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
            g.fill(getX(), getY() + 4, getX() + 4, getY() + getHeight() - 4, 0xFF5577CC);
            g.drawString(Minecraft.getInstance().font,
                    Component.literal(text).withStyle(Style.EMPTY.withColor(0xFFAA00).withBold(true)), getX() + 8,
                    getY() + 4, 0xFFFFFF, false);
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